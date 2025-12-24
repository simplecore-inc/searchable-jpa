package dev.simplecore.searchable.core.service.specification;

import lombok.extern.slf4j.Slf4j;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EntityType;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Manages EntityGraph creation and application for optimal relationship loading.
 * Handles both simple and nested relationship paths while preventing memory pagination issues.
 */
@Slf4j
public class EntityGraphManager<T> {

    private final EntityManager entityManager;
    private final Class<T> entityClass;

    public EntityGraphManager(EntityManager entityManager, Class<T> entityClass) {
        this.entityManager = entityManager;
        this.entityClass = entityClass;
    }

    /**
     * Creates dynamic EntityGraph with ToOne relationships only.
     * ToMany relationships are excluded to prevent HHH000104 warning and memory pagination.
     */
    public jakarta.persistence.EntityGraph<T> createDynamicEntityGraph(Set<String> relationshipPaths) {
        try {
            jakarta.persistence.EntityGraph<T> entityGraph = entityManager.createEntityGraph(entityClass);
            Set<String> toOneOnlyPaths = new HashSet<>();

            for (String path : relationshipPaths) {
                try {
                    // Only include ToOne relationships in EntityGraph
                    if (!isToManyPathForEntityGraph(path)) {
                        if (path.contains(".")) {
                            // Handle nested paths in EntityGraph
                            addNestedPathToEntityGraph(entityGraph, path);
                        } else {
                            // Simple path
                            entityGraph.addAttributeNodes(path);
                            log.trace("Added ToOne attribute '{}' to EntityGraph", path);
                        }
                        toOneOnlyPaths.add(path);
                    } else {
                        log.trace("Skipped ToMany path '{}' from EntityGraph to prevent memory pagination", path);
                    }
                } catch (Exception e) {
                    log.warn("Failed to add path '{}' to EntityGraph: {}", path, e.getMessage());
                }
            }

            log.info("Created dynamic EntityGraph with {} ToOne relationship paths (excluded {} ToMany paths)",
                    toOneOnlyPaths.size(), relationshipPaths.size() - toOneOnlyPaths.size());
            return entityGraph;

        } catch (Exception e) {
            log.warn("Failed to create dynamic EntityGraph: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Applies dynamic EntityGraph to a query for optimized relationship loading.
     */
    public void applyDynamicEntityGraph(TypedQuery<T> query, Set<String> relationshipPaths) {
        if (relationshipPaths.isEmpty()) {
            return;
        }

        try {
            jakarta.persistence.EntityGraph<T> entityGraph = createDynamicEntityGraph(relationshipPaths);
            if (entityGraph != null) {
                query.setHint("jakarta.persistence.fetchgraph", entityGraph);
                log.info("Applied dynamic EntityGraph with {} paths to query", relationshipPaths.size());
            }
        } catch (Exception e) {
            log.warn("Failed to apply dynamic EntityGraph: {}", e.getMessage());
        }
    }

    /**
     * Checks if a path represents a ToMany relationship without requiring a Root parameter.
     * This is specifically for EntityGraph creation where Root is not available.
     */
    private boolean isToManyPathForEntityGraph(String path) {
        if (path == null || path.trim().isEmpty()) {
            return false;
        }

        try {
            String[] parts = path.split("\\.");
            Class<?> currentType = entityClass;

            for (String part : parts) {
                EntityType<?> entityType = entityManager.getMetamodel().entity(currentType);
                Attribute<?, ?> attribute = entityType.getAttribute(part);

                if (attribute.isCollection()) {
                    return true;
                }

                currentType = attribute.getJavaType();
            }

            return false;
        } catch (Exception e) {
            log.trace("Error analyzing path '{}' for ToMany check: {}", path, e.getMessage());
            // If we can't determine, assume it's ToMany to be safe
            return true;
        }
    }

    /**
     * Adds nested paths to EntityGraph using subgraphs.
     */
    private void addNestedPathToEntityGraph(jakarta.persistence.EntityGraph<T> entityGraph, String nestedPath) {
        String[] pathParts = nestedPath.split("\\.");

        if (pathParts.length < 2) {
            entityGraph.addAttributeNodes(nestedPath);
            return;
        }

        // Create subgraph for the first part
        String firstPart = pathParts[0];
        jakarta.persistence.Subgraph<?> subgraph;

        try {
            subgraph = entityGraph.addSubgraph(firstPart);
        } catch (Exception e) {
            // If subgraph creation fails, try adding as simple attribute
            entityGraph.addAttributeNodes(firstPart);
            log.trace("Added '{}' as simple attribute instead of subgraph", firstPart);
            return;
        }

        // Build remaining path
        String remainingPath = String.join(".", Arrays.copyOfRange(pathParts, 1, pathParts.length));

        if (remainingPath.contains(".")) {
            // Still nested, create another subgraph
            addNestedPathToSubgraph(subgraph, remainingPath);
        } else {
            // Final part, add as attribute
            subgraph.addAttributeNodes(remainingPath);
            log.trace("Added nested path '{}' to EntityGraph", nestedPath);
        }
    }

    /**
     * Recursively adds nested paths to subgraphs.
     */
    private void addNestedPathToSubgraph(jakarta.persistence.Subgraph<?> parentSubgraph, String nestedPath) {
        String[] pathParts = nestedPath.split("\\.");

        if (pathParts.length == 1) {
            parentSubgraph.addAttributeNodes(nestedPath);
            return;
        }

        String firstPart = pathParts[0];
        String remainingPath = String.join(".", Arrays.copyOfRange(pathParts, 1, pathParts.length));

        try {
            jakarta.persistence.Subgraph<?> subgraph = parentSubgraph.addSubgraph(firstPart);
            addNestedPathToSubgraph(subgraph, remainingPath);
        } catch (Exception e) {
            // Fallback: add as simple attribute
            parentSubgraph.addAttributeNodes(firstPart);
            log.trace("Added '{}' as simple attribute in subgraph", firstPart);
        }
    }
} 