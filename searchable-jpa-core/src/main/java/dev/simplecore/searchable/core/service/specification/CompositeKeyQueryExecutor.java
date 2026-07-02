package dev.simplecore.searchable.core.service.specification;

import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.exception.SearchableOperationException;
import dev.simplecore.searchable.core.i18n.MessageUtils;
import dev.simplecore.searchable.core.utils.SearchableFieldUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceUnitUtil;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Handles two-phase pagination for composite-key entities ({@code @IdClass} and {@code @EmbeddedId}).
 * <p>
 * The {@code @EmbeddedId} attribute name is resolved from the JPA metamodel (never assumed to be
 * {@code "id"}), Phase 1 uses {@code GROUP BY} with deterministic aggregates so sorting through a
 * to-many relationship cannot duplicate or drop keys across pages, and the distinct total count is
 * computed with a portable concatenated-key expression.
 */
@Slf4j
class CompositeKeyQueryExecutor<T> {

    private final SearchCondition<?> condition;
    private final EntityManager entityManager;
    private final Class<T> entityClass;
    private final JpaSpecificationExecutor<T> specificationExecutor;
    private final JoinStrategyManager<T> joinStrategyManager;

    private final List<String> idFields;
    private final boolean embeddedId;
    private final String embeddedIdAttribute;

    CompositeKeyQueryExecutor(SearchCondition<?> condition,
                              EntityManager entityManager,
                              Class<T> entityClass,
                              JpaSpecificationExecutor<T> specificationExecutor,
                              JoinStrategyManager<T> joinStrategyManager) {
        this.condition = condition;
        this.entityManager = entityManager;
        this.entityClass = entityClass;
        this.specificationExecutor = specificationExecutor;
        this.joinStrategyManager = joinStrategyManager;
        this.idFields = SearchableFieldUtils.getCompositeKeyFieldNames(entityManager, entityClass);
        this.embeddedId = SearchableFieldUtils.isEmbeddedIdEntity(entityManager, entityClass);
        this.embeddedIdAttribute = embeddedId
                ? SearchableFieldUtils.getEmbeddedIdAttributeName(entityManager, entityClass)
                : null;
    }

    boolean hasIdFields() {
        return !idFields.isEmpty();
    }

    /**
     * Resolves the Criteria path for a single composite-id field, honoring the actual
     * {@code @EmbeddedId} attribute name for embedded keys.
     */
    private Path<?> idFieldPath(Root<T> root, String field) {
        return embeddedId ? root.get(embeddedIdAttribute).get(field) : root.get(field);
    }

    /**
     * Phase 1: fetch the distinct composite keys for the requested page, ordered deterministically.
     */
    List<Object> executePhaseOne(PageRequest pageRequest, Set<String> joinPaths) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Object[]> query = cb.createQuery(Object[].class);
        Root<T> root = query.from(entityClass);
        joinStrategyManager.applyRegularJoinsOnly(root, joinPaths);

        List<Selection<?>> selections = new ArrayList<>();
        List<Expression<?>> groupBy = new ArrayList<>();
        for (String idField : idFields) {
            Path<?> path = idFieldPath(root, idField);
            selections.add(path);
            groupBy.add(path);
        }

        List<Order> orders = new ArrayList<>();
        Set<String> sortedIdFields = new LinkedHashSet<>();
        for (Sort.Order sortOrder : pageRequest.getSort()) {
            String property = sortOrder.getProperty();
            if (SearchableFieldUtils.COMPOSITE_KEY_MARKER.equals(property)) {
                continue;
            }
            if (idFields.contains(property)) {
                Path<?> path = idFieldPath(root, property);
                orders.add(sortOrder.isAscending() ? cb.asc(path) : cb.desc(path));
                sortedIdFields.add(property);
            } else {
                Path<?> sortPath = SpecificationQuerySupport.getPath(root, property);
                Expression<?> aggregate = SpecificationQuerySupport.aggregateForSort(cb, sortPath, sortOrder.isAscending());
                selections.add(aggregate);
                orders.add(sortOrder.isAscending() ? cb.asc(aggregate) : cb.desc(aggregate));
            }
        }
        // Deterministic tiebreaker: order by every id field not already ordered.
        for (String idField : idFields) {
            if (!sortedIdFields.contains(idField)) {
                orders.add(cb.asc(idFieldPath(root, idField)));
            }
        }

        query.multiselect(selections);
        Predicate predicate = SpecificationQuerySupport.createPredicates(condition, entityManager, root, query, cb);
        if (predicate != null) {
            query.where(predicate);
        }
        query.groupBy(groupBy);
        query.orderBy(orders);

        TypedQuery<Object[]> typedQuery = entityManager.createQuery(query);
        typedQuery.setFirstResult((int) pageRequest.getOffset());
        typedQuery.setMaxResults(pageRequest.getPageSize());

        return typedQuery.getResultList().stream()
                .map(this::toKey)
                .collect(Collectors.toList());
    }

    private Object toKey(Object[] row) {
        if (idFields.size() == 1) {
            return row[0];
        }
        Object[] compositeKey = new Object[idFields.size()];
        System.arraycopy(row, 0, compositeKey, 0, idFields.size());
        return compositeKey;
    }

    /**
     * Builds a portable distinct-count key expression by concatenating the string-cast id fields.
     * SQL Server does not support multi-column {@code COUNT(DISTINCT ...)} and JPA Criteria cannot
     * express a derived table, so a single concatenated expression is used with {@code countDistinct}.
     */
    Expression<String> buildDistinctCountKeyExpression(Root<T> root, CriteriaBuilder cb) {
        Expression<String> concatenated = null;
        for (String idField : idFields) {
            Expression<String> part = idFieldPath(root, idField).as(String.class);
            concatenated = (concatenated == null)
                    ? part
                    : cb.concat(cb.concat(concatenated, "|"), part);
        }
        return concatenated;
    }

    /**
     * Phase 2: load the full entities for the given composite keys, applying fetch joins and
     * {@code distinct} to avoid emitting duplicate root rows from to-many fetches. Ordering is
     * restored by the caller from the Phase 1 key order, so no {@code ORDER BY} is applied here.
     */
    List<T> executePhaseTwo(List<Object> ids, Set<String> fetchFields) {
        Specification<T> spec = (root, query, cb) -> {
            query.distinct(true);
            SpecificationQuerySupport.applyFetchJoins(root, query, fetchFields);

            List<Predicate> orPredicates = new ArrayList<>();
            for (Object id : ids) {
                if (id instanceof Object[]) {
                    Object[] compositeKey = (Object[]) id;
                    if (compositeKey.length == idFields.size()) {
                        List<Predicate> andPredicates = new ArrayList<>();
                        for (int i = 0; i < idFields.size(); i++) {
                            andPredicates.add(cb.equal(idFieldPath(root, idFields.get(i)), compositeKey[i]));
                        }
                        orPredicates.add(cb.and(andPredicates.toArray(new Predicate[0])));
                    } else {
                        log.warn("Composite key length mismatch: expected {}, got {}", idFields.size(), compositeKey.length);
                    }
                } else if (idFields.size() == 1) {
                    orPredicates.add(cb.equal(idFieldPath(root, idFields.get(0)), id));
                } else {
                    log.warn("Expected composite key array but got single value: {}", id);
                }
            }

            return orPredicates.isEmpty() ? cb.disjunction() : cb.or(orPredicates.toArray(new Predicate[0]));
        };

        return specificationExecutor.findAll(spec, Sort.unsorted());
    }

    /**
     * Extracts the composite key of a managed entity in the same representation produced by
     * {@link #executePhaseOne} (a single value or an {@code Object[]}), so results can be reordered.
     */
    Object extractId(T entity) {
        if (idFields.size() == 1) {
            return extractField(entity, idFields.get(0));
        }
        Object[] compositeKey = new Object[idFields.size()];
        for (int i = 0; i < idFields.size(); i++) {
            compositeKey[i] = extractField(entity, idFields.get(i));
        }
        return compositeKey;
    }

    private Object extractField(T entity, String fieldName) {
        if (embeddedId) {
            PersistenceUnitUtil unitUtil = entityManager.getEntityManagerFactory().getPersistenceUnitUtil();
            Object embedded = unitUtil.getIdentifier(entity);
            if (embedded == null) {
                throw new SearchableOperationException(
                        MessageUtils.getMessage("executor.embedded.id.null", new Object[]{entityClass.getSimpleName()}));
            }
            return readProperty(embedded, fieldName);
        }
        return readProperty(entity, fieldName);
    }

    private Object readProperty(Object target, String fieldName) {
        String getterName = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
        try {
            Method getter = target.getClass().getMethod(getterName);
            return getter.invoke(target);
        } catch (NoSuchMethodException e) {
            try {
                Field field = target.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (ReflectiveOperationException ex) {
                throw new SearchableOperationException(
                        MessageUtils.getMessage("executor.composite.key.not.found",
                                new Object[]{entityClass.getSimpleName()}), ex);
            }
        } catch (ReflectiveOperationException e) {
            throw new SearchableOperationException(
                    MessageUtils.getMessage("executor.composite.key.not.found",
                            new Object[]{entityClass.getSimpleName()}), e);
        }
    }
}
