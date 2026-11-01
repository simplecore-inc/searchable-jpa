package dev.simplecore.searchable.core.service.aggregate;

import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.service.specification.MatchingRows;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Counts and sums rows under the same conditions the list beside them is read with.
 *
 * <p>A card above a list ("Failed: 12", "Balance held: 3,400") has to agree with the list under
 * it, so narrowing the list to one customer narrows the card too. The figures are therefore built
 * from the list's own {@link SearchCondition} and computed in the database, like
 * {@link dev.simplecore.searchable.core.service.bucket.TimeBucketCounter}.
 */
public class SearchAggregator {

    /** What an aggregate computes over one field. */
    public enum Op {
        SUM, COUNT, COUNT_DISTINCT, MIN, MAX;

        /**
         * @param name {@code sum}, {@code count}, {@code countDistinct}, {@code min} or {@code max}
         * @return the operation
         * @throws IllegalArgumentException for any other name
         */
        public static Op parse(String name) {
            return switch (name) {
                case "sum" -> SUM;
                case "count" -> COUNT;
                case "countDistinct" -> COUNT_DISTINCT;
                case "min" -> MIN;
                case "max" -> MAX;
                default -> throw new IllegalArgumentException("Unknown aggregate operation: " + name);
            };
        }

        /** @return the name {@link #parse} reads */
        public String wireName() {
            return this == COUNT_DISTINCT ? "countDistinct" : name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * One figure to compute.
     *
     * @param field the entity field
     * @param op what to compute over it
     */
    public record Aggregate(String field, Op op) {
    }

    /**
     * One computed figure.
     *
     * @param field the entity field
     * @param op the operation's wire name
     * @param value the figure; a sum or count of no rows is 0, a min or max of no rows is null
     */
    public record AggregateValue(String field, String op, Number value) {
    }

    /**
     * How many rows hold one value.
     *
     * @param value the field's value
     * @param count the rows holding it
     */
    public record ValueCount(Object value, long count) {
    }

    private final EntityManager entityManager;

    /**
     * @param entityManager where the query is run
     */
    public SearchAggregator(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * Counts matching rows per value of one field.
     *
     * <p>An enum field yields one entry per constant in declaration order, zero for a constant no
     * row holds, so a card for a state nothing is in still shows; a boolean yields {@code true}
     * then {@code false}. Any other field yields only the values present, most frequent first. A
     * row whose field is null is not counted.
     *
     * @param entityType the entity being counted
     * @param repository the entity's repository, which the condition is compiled against
     * @param condition what the list is currently filtered by
     * @param field the entity field to group by
     * @param <T> the entity type
     * @return one count per value
     */
    public <T> List<ValueCount> countByValue(Class<T> entityType, JpaSpecificationExecutor<T> repository,
                                             SearchCondition<?> condition, String field) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<T> root = query.from(entityType);
        Path<Object> value = root.get(field);
        Expression<Long> total = cb.count(root);
        query.multiselect(value.alias("value"), total.alias("total"))
                .where(cb.and(cb.isNotNull(value), matching(cb, query, root, entityType, repository, condition)))
                .groupBy(value)
                .orderBy(cb.desc(total));

        Map<Object, Long> counted = new LinkedHashMap<>();
        for (Tuple row : entityManager.createQuery(query).getResultList()) {
            counted.put(row.get("value"), row.get("total", Number.class).longValue());
        }

        Class<?> type = value.getJavaType();
        List<Object> order = type.isEnum()
                ? Arrays.asList(type.getEnumConstants())
                : type == Boolean.class || type == boolean.class
                ? List.of(Boolean.TRUE, Boolean.FALSE)
                : new ArrayList<>(counted.keySet());
        return order.stream().map(v -> new ValueCount(v, counted.getOrDefault(v, 0L))).toList();
    }

    /**
     * Computes each requested figure over the matching rows, in one query.
     *
     * @param entityType the entity being aggregated
     * @param repository the entity's repository, which the condition is compiled against
     * @param condition what the list is currently filtered by
     * @param aggregates the figures, in the order they are returned
     * @param <T> the entity type
     * @return one value per requested figure
     */
    public <T> List<AggregateValue> aggregate(Class<T> entityType, JpaSpecificationExecutor<T> repository,
                                              SearchCondition<?> condition, List<Aggregate> aggregates) {
        if (aggregates.isEmpty()) {
            return List.of();
        }
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<T> root = query.from(entityType);
        List<Selection<?>> selections = new ArrayList<>();
        for (int i = 0; i < aggregates.size(); i++) {
            selections.add(expression(cb, root, aggregates.get(i)).alias("a" + i));
        }
        query.multiselect(selections).where(matching(cb, query, root, entityType, repository, condition));
        Tuple row = entityManager.createQuery(query).getSingleResult();

        List<AggregateValue> values = new ArrayList<>();
        for (int i = 0; i < aggregates.size(); i++) {
            Aggregate aggregate = aggregates.get(i);
            Number value = row.get("a" + i, Number.class);
            // A sum over no rows is null in SQL; the card reads it as nothing, which is zero.
            if (value == null && aggregate.op() == Op.SUM) {
                value = 0L;
            }
            values.add(new AggregateValue(aggregate.field(), aggregate.op().wireName(), value));
        }
        return values;
    }

    private static <T> Expression<? extends Number> expression(CriteriaBuilder cb, Root<T> root, Aggregate aggregate) {
        return switch (aggregate.op()) {
            case SUM -> cb.sum(root.<Number>get(aggregate.field()));
            case COUNT -> cb.count(root.get(aggregate.field()));
            case COUNT_DISTINCT -> cb.countDistinct(root.get(aggregate.field()));
            case MIN -> cb.min(root.<Number>get(aggregate.field()));
            case MAX -> cb.max(root.<Number>get(aggregate.field()));
        };
    }

    private <T> Predicate matching(CriteriaBuilder cb, CriteriaQuery<?> query, Root<T> root,
                                                                Class<T> entityType,
                                                                JpaSpecificationExecutor<T> repository,
                                                                SearchCondition<?> condition) {
        return MatchingRows.of(cb, query, root, entityType, repository, entityManager, condition)
                .orElseGet(cb::conjunction);
    }
}
