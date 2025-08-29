package dev.simplecore.searchable.core.service.specification;

import dev.simplecore.searchable.core.condition.SearchCondition.Condition;
import dev.simplecore.searchable.core.condition.SearchCondition.Group;
import dev.simplecore.searchable.core.condition.SearchCondition.Node;
import dev.simplecore.searchable.core.condition.operator.LogicalOperator;
import dev.simplecore.searchable.core.exception.SearchableOperationException;
import lombok.NonNull;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

public class SpecificationBuilder<T> {
    private final PredicateBuilder<T> predicateBuilder;

    public SpecificationBuilder(@NonNull PredicateBuilder<T> predicateBuilder) {
        this.predicateBuilder = predicateBuilder;
    }

    /**
     * Creates appropriate Predicate based on Node type.
     * - For Group: Process inner nodes recursively and combine with AND/OR
     * - For Condition: Convert to a single condition using PredicateBuilder
     */
    public Predicate build(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb, Node node) {
        if (node == null) {
            return null;
        }

        try {
            if (node instanceof Group) {
                return buildGroup((Group) node, root, query, cb);
            }
            if (node instanceof Condition) {
                return buildCondition((Condition) node);
            }
            throw new SearchableOperationException(String.format(
                    "Unknown node type: %s. Expected Group or Condition.",
                    node.getClass().getSimpleName()
            ));
        } catch (IllegalArgumentException e) {
            throw new SearchableOperationException(String.format(
                    "Invalid field or operator in node: %s", node
            ), e);
        } catch (Exception e) {
            throw new SearchableOperationException(String.format(
                    "Failed to build predicate for node: %s. Cause: %s",
                    node, e.getMessage()
            ), e);
        }
    }

    private Predicate buildCondition(Condition condition) {
        if (condition == null) {
            throw new SearchableOperationException("Condition cannot be null");
        }

        handleJoinPath(condition);

        try {
            return predicateBuilder.build(condition);
        } catch (Exception e) {
            throw new SearchableOperationException("Failed to build predicate for condition: " + condition, e);
        }
    }

    private void handleJoinPath(Condition condition) {
        String field = condition.getField();
        if (field == null) {
            throw new SearchableOperationException("Field must not be null");
        }
    }

    private Predicate buildGroup(Group group, Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        if (group.getNodes() == null || group.getNodes().isEmpty()) {
            throw new SearchableOperationException("Group must have at least one node");
        }

        // Process the first node
        Node firstNode = group.getNodes().get(0);
        Predicate result = build(root, query, cb, firstNode);
        if (result == null) {
            return null;
        }

        // Process remaining nodes in order, combining them with their respective operators
        for (int i = 1; i < group.getNodes().size(); i++) {
            Node currentNode = group.getNodes().get(i);
            Predicate currentPredicate = build(root, query, cb, currentNode);
            if (currentPredicate == null) continue;

            if (currentNode.getOperator() == LogicalOperator.OR) {
                result = cb.or(result, currentPredicate);
            } else {
                result = cb.and(result, currentPredicate);
            }
        }

        return result;
    }
} 