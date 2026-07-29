package dev.simplecore.searchable.core.service.bucket;

import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.service.specification.SearchableSpecificationBuilder;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.query.sqm.function.SqmFunctionDescriptor;
import org.hibernate.query.sqm.function.SqmFunctionRegistry;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Counts rows per slice of a time window, under the same conditions the list beside them is
 * read with.
 *
 * <p>A time-range selector draws a strip of how busy each slice of a window was, and the strip
 * has to agree with the list under it — narrowing to one customer must thin the strip too. So
 * the count is built from the screen's own {@link SearchCondition} rather than from a second set
 * of parameters that could drift from it.
 *
 * <p>Counted in the database. A window can hold far more rows than are worth carrying into
 * memory to divide by time, and counting is what a database is for.
 *
 * <h2>Two forms, and why</h2>
 *
 * <p>Where {@link EpochMillisFunctionContributor} could express the stored instant as a number,
 * the count is one {@code group by} over an arithmetic bucket: the database reads each row in the
 * window once and does one division on it. That is the form that holds up as a table grows.
 *
 * <p>Where it could not — a database this library has no entry for — the count falls back to one
 * conditional sum per slice. Correct, needs nothing but instant comparison, and costs a
 * comparison per row per slice; acceptable for a small table and the reason an unknown database
 * still works rather than failing.
 *
 * <p>Either way the window wants an index on the field it is measured over. Without one, every
 * strip is a full scan of the table no matter which form runs.
 */
public class TimeBucketCounter {

    /**
     * The most slices one call may ask for.
     *
     * <p>A strip is read by eye and there is no reading a thousand columns of it. The ceiling
     * also bounds the width of the fallback statement, which carries one sum per slice.
     */
    public static final int MAX_BUCKETS = 512;

    private final EntityManager entityManager;

    /**
     * @param entityManager where the query is run
     */
    public TimeBucketCounter(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * Counts matching rows per bucket across a window.
     *
     * <p>The window is half-open — a row at exactly {@code to} belongs to the next window, so
     * scrubbing from one window to the next never counts a row twice. Every slice is half-open
     * the same way, so a row on a boundary is counted once, by the later slice.
     *
     * @param entityType the entity being counted
     * @param repository the entity's repository, which the condition is compiled against
     * @param condition what the screen is currently filtered by
     * @param timeAxisField the entity field the window is measured on, an {@code Instant}
     * @param from the first instant in the window, inclusive
     * @param to the instant the window ends at, exclusive
     * @param buckets how many slices to divide the window into
     * @param <T> the entity type
     * @return one count per bucket, oldest first, always {@code buckets} long
     * @throws IllegalArgumentException when the window is empty or reversed, or the bucket count
     *         is outside 1..{@value #MAX_BUCKETS}
     */
    public <T> List<Long> count(Class<T> entityType,
                                JpaSpecificationExecutor<T> repository,
                                SearchCondition<?> condition,
                                String timeAxisField,
                                Instant from, Instant to, int buckets) {
        requireWindow(from, to, buckets);

        return canReadInstantAsNumber()
                ? countByGrouping(entityType, repository, condition, timeAxisField, from, to, buckets)
                : countByConditionalSums(entityType, repository, condition, timeAxisField, from, to, buckets);
    }

    /**
     * One pass over the window, one division per row.
     *
     * <p>Elapsed time is multiplied by the bucket count before being divided by the window, so a
     * window that does not divide evenly still produces slices of even width — dividing first
     * would round the width down and leave the last slice absorbing the remainder.
     */
    private <T> List<Long> countByGrouping(Class<T> entityType,
                                           JpaSpecificationExecutor<T> repository,
                                           SearchCondition<?> condition,
                                           String timeAxisField,
                                           Instant from, Instant to, int buckets) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<T> root = query.from(entityType);
        Expression<Instant> axis = root.get(timeAxisField);

        long start = from.toEpochMilli();
        long span = to.toEpochMilli() - start;

        Expression<Long> millis = cb.function(
                EpochMillisFunctionContributor.FUNCTION_NAME, Long.class, axis);
        Expression<Number> scaled = cb.prod(cb.diff(millis, start), (long) buckets);
        Expression<Long> bucket = cb.quot(scaled, span).as(Long.class);

        query.multiselect(bucket.alias("bucket"), cb.count(root).alias("total"))
                .where(window(cb, query, root, axis, repository, entityType, condition, from, to))
                .groupBy(bucket);

        long[] counts = new long[buckets];
        for (Tuple row : entityManager.createQuery(query).getResultList()) {
            Number index = row.get("bucket", Number.class);
            Number total = row.get("total", Number.class);
            if (index == null || total == null) {
                continue;
            }
            int slot = index.intValue();
            // The predicates cannot let a row fall outside the window, but a rounding edge would
            // corrupt memory rather than the answer if one ever did.
            if (slot >= 0 && slot < buckets) {
                counts[slot] = total.longValue();
            }
        }
        return boxed(counts);
    }

    /**
     * One conditional sum per slice, for a database whose stored instant this library cannot
     * express as a number.
     */
    private <T> List<Long> countByConditionalSums(Class<T> entityType,
                                                  JpaSpecificationExecutor<T> repository,
                                                  SearchCondition<?> condition,
                                                  String timeAxisField,
                                                  Instant from, Instant to, int buckets) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<T> root = query.from(entityType);
        Expression<Instant> axis = root.get(timeAxisField);

        Instant[] edges = edges(from, to, buckets);
        List<Selection<?>> slices = new ArrayList<>(buckets);
        for (int i = 0; i < buckets; i++) {
            Predicate within = cb.and(cb.greaterThanOrEqualTo(axis, edges[i]),
                    cb.lessThan(axis, edges[i + 1]));
            Expression<Long> counted = cb.<Long>selectCase()
                    .when(within, 1L)
                    .otherwise(0L)
                    .as(Long.class);
            slices.add(cb.sum(counted).alias(alias(i)));
        }

        query.multiselect(slices)
                .where(window(cb, query, root, axis, repository, entityType, condition, from, to));

        Tuple row = entityManager.createQuery(query).getSingleResult();
        long[] counts = new long[buckets];
        for (int i = 0; i < buckets; i++) {
            // A sum over no rows at all is null, which is a window nothing happened in.
            Number total = row.get(alias(i), Number.class);
            counts[i] = total == null ? 0L : total.longValue();
        }
        return boxed(counts);
    }

    /**
     * The window's own bounds, and whatever the screen is filtered by on top of them.
     */
    private <T> Predicate window(CriteriaBuilder cb, CriteriaQuery<?> query, Root<T> root,
                                 Expression<Instant> axis, JpaSpecificationExecutor<T> repository,
                                 Class<T> entityType, SearchCondition<?> condition,
                                 Instant from, Instant to) {
        List<Predicate> conditions = new ArrayList<>();
        conditions.add(cb.greaterThanOrEqualTo(axis, from));
        conditions.add(cb.lessThan(axis, to));

        Specification<T> filter = SearchableSpecificationBuilder
                .of(condition, entityManager, entityType, repository)
                .buildSpecificationOnly()
                .getSpecification();
        Predicate filterPredicate = filter == null ? null : filter.toPredicate(root, query, cb);
        if (filterPredicate != null) {
            conditions.add(filterPredicate);
        }
        return cb.and(conditions.toArray(new Predicate[0]));
    }

    /**
     * @return whether this database has an entry in {@link EpochMillisFunctionContributor}
     */
    private boolean canReadInstantAsNumber() {
        SqmFunctionRegistry registry = entityManager.getEntityManagerFactory()
                .unwrap(SessionFactoryImplementor.class)
                .getQueryEngine()
                .getSqmFunctionRegistry();
        SqmFunctionDescriptor descriptor =
                registry.findFunctionDescriptor(EpochMillisFunctionContributor.FUNCTION_NAME);
        return descriptor != null;
    }

    /**
     * The instants the slices begin and end at, {@code buckets + 1} of them.
     *
     * <p>Each edge is computed from the whole window rather than by adding a rounded width, so a
     * window that does not divide evenly spreads the remainder across the slices instead of
     * letting the last one absorb it. The final edge is the window's own end exactly.
     */
    private Instant[] edges(Instant from, Instant to, int buckets) {
        long start = from.toEpochMilli();
        long span = to.toEpochMilli() - start;

        Instant[] edges = new Instant[buckets + 1];
        for (int i = 0; i < buckets; i++) {
            edges[i] = Instant.ofEpochMilli(start + Math.floorDiv(span * i, buckets));
        }
        edges[buckets] = to;
        return edges;
    }

    private static List<Long> boxed(long[] counts) {
        List<Long> result = new ArrayList<>(counts.length);
        for (long count : counts) {
            result.add(count);
        }
        return result;
    }

    private static String alias(int bucket) {
        return "b" + bucket;
    }

    private void requireWindow(Instant from, Instant to, int buckets) {
        if (from == null || to == null || !to.isAfter(from)) {
            throw new IllegalArgumentException("time window must start before it ends");
        }
        if (buckets < 1 || buckets > MAX_BUCKETS) {
            throw new IllegalArgumentException("bucket count must be between 1 and " + MAX_BUCKETS);
        }
    }
}
