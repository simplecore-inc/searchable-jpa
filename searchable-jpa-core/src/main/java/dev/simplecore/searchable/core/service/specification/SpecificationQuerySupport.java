package dev.simplecore.searchable.core.service.specification;

import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.operator.LogicalOperator;
import dev.simplecore.searchable.core.service.join.JoinManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import java.util.List;
import java.util.Set;

/**
 * Shared Criteria helpers used by the two-phase query executors:
 * building the search predicate tree, resolving (possibly nested) property paths,
 * applying Phase 2 fetch joins, and producing deterministic aggregate sort expressions.
 */
final class SpecificationQuerySupport {

    private static final Logger log = LoggerFactory.getLogger(SpecificationQuerySupport.class);

    private SpecificationQuerySupport() {
    }

    /**
     * Builds the combined predicate for the given search condition, folding the top-level nodes
     * left-to-right using each node's logical operator.
     *
     * @return the combined predicate, or {@code null} if there are no conditions
     */
    static <T> Predicate createPredicates(SearchCondition<?> condition, EntityManager entityManager,
                                          Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        List<SearchCondition.Node> nodes = condition.getNodes();
        if (nodes == null || nodes.isEmpty()) {
            return null;
        }

        JoinManager<T> joinManager = new JoinManager<>(entityManager, root);
        PredicateBuilder<T> predicateBuilder = new PredicateBuilder<>(cb, joinManager);
        SpecificationBuilder<T> specBuilder = new SpecificationBuilder<>(predicateBuilder);

        SearchCondition.Node firstNode = nodes.get(0);
        Predicate result = specBuilder.build(root, query, cb, firstNode);
        if (result == null) {
            return null;
        }

        for (int i = 1; i < nodes.size(); i++) {
            SearchCondition.Node currentNode = nodes.get(i);
            Predicate currentPredicate = specBuilder.build(root, query, cb, currentNode);
            if (currentPredicate == null) {
                continue;
            }

            if (currentNode.getOperator() == LogicalOperator.OR) {
                result = cb.or(result, currentPredicate);
            } else {
                result = cb.and(result, currentPredicate);
            }
        }

        return result;
    }

    /**
     * Resolves a (possibly nested) property path against the given root, creating LEFT JOINs for
     * intermediate relationship segments and reusing existing joins where possible.
     */
    static Path<?> getPath(From<?, ?> from, String property) {
        if (!property.contains(".")) {
            return from.get(property);
        }

        String[] parts = property.split("\\.");
        Path<?> path = from;

        for (int i = 0; i < parts.length - 1; i++) {
            if (path instanceof From) {
                From<?, ?> current = (From<?, ?>) path;
                Join<?, ?> join = null;

                for (Join<?, ?> existingJoin : current.getJoins()) {
                    if (existingJoin.getAttribute().getName().equals(parts[i])) {
                        join = existingJoin;
                        break;
                    }
                }

                if (join == null) {
                    join = current.join(parts[i], JoinType.LEFT);
                }
                path = join;
            }
        }

        return path.get(parts[parts.length - 1]);
    }

    /**
     * Produces a deterministic aggregate over a sort path so that traversing a to-many relationship
     * for sorting collapses to a single value per group ({@code least} for ascending, {@code greatest}
     * for descending). This keeps ID-level pagination stable even when the sort path is to-many.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static Expression<?> aggregateForSort(CriteriaBuilder cb, Path<?> path, boolean ascending) {
        return ascending ? cb.least((Expression) path) : cb.greatest((Expression) path);
    }

    /**
     * Applies Phase 2 fetch joins for the given fields (supporting nested {@code a.b.c} paths),
     * skipping count queries and already-applied fetches. Failures for individual fields are logged
     * and skipped rather than aborting the query.
     */
    static void applyFetchJoins(Root<?> root, CriteriaQuery<?> query, Set<String> fetchFields) {
        if (fetchFields == null || fetchFields.isEmpty() || Long.class.equals(query.getResultType())) {
            return;
        }

        for (String fetchField : fetchFields) {
            try {
                if (fetchField.contains(".")) {
                    applyNestedFetchJoin(root, fetchField);
                } else {
                    boolean alreadyFetched = root.getFetches().stream()
                            .anyMatch(fetch -> fetch.getAttribute().getName().equals(fetchField));
                    if (!alreadyFetched) {
                        root.fetch(fetchField, JoinType.LEFT);
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to apply fetch join for field '{}': {}", fetchField, e.getMessage());
            }
        }
    }

    private static void applyNestedFetchJoin(Root<?> root, String nestedPath) {
        String[] pathParts = nestedPath.split("\\.");
        From<?, ?> currentFrom = root;

        for (String part : pathParts) {
            boolean alreadyFetched = currentFrom.getFetches().stream()
                    .anyMatch(fetch -> fetch.getAttribute().getName().equals(part));

            if (!alreadyFetched) {
                currentFrom = (From<?, ?>) currentFrom.fetch(part, JoinType.LEFT);
            } else {
                currentFrom = (From<?, ?>) currentFrom.getFetches().stream()
                        .filter(fetch -> fetch.getAttribute().getName().equals(part))
                        .findFirst()
                        .orElse(null);
                if (currentFrom == null) {
                    log.warn("Expected fetch not found for path part: {}", part);
                    return;
                }
            }
        }
    }
}
