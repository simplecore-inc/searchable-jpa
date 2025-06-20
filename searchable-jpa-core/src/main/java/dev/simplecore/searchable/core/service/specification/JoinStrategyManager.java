package dev.simplecore.searchable.core.service.specification;

import dev.simplecore.searchable.core.condition.SearchCondition;
import lombok.extern.slf4j.Slf4j;

import javax.persistence.EntityManager;
import javax.persistence.criteria.From;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Root;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Manages JOIN strategies for optimal query performance.
 * Handles ToOne vs ToMany relationship optimization and prevents N+1 problems.
 */
@Slf4j
public class JoinStrategyManager<T> {

    private final EntityManager entityManager;
    private final Class<T> entityClass;
    private final RelationshipAnalyzer<T> relationshipAnalyzer;

    public JoinStrategyManager(EntityManager entityManager, Class<T> entityClass) {
        this.entityManager = entityManager;
        this.entityClass = entityClass;
        this.relationshipAnalyzer = new RelationshipAnalyzer<>(entityManager, entityClass);
    }

    /**
     * Apply improved join strategy for ToMany relationships.
     * Uses different strategies based on the number of ToMany relationships.
     */
    public void applyJoins(Root<T> root, Set<String> paths, boolean isCountQuery) {
        root.getJoins().clear();

        // Separate ToOne and ToMany paths
        Set<String> toOnePaths = new HashSet<>();
        Set<String> toManyPaths = new HashSet<>();

        for (String path : paths) {
            if (relationshipAnalyzer.isToManyPath(root, path)) {
                toManyPaths.add(path);
            } else {
                toOnePaths.add(path);
            }
        }

        log.debug("Detected ToOne paths: {}", toOnePaths);
        log.debug("Detected ToMany paths: {}", toManyPaths);

        // Apply ToOne joins
        for (String path : toOnePaths) {
            try {
                if (isCountQuery) {
                    // For count queries, use regular join to avoid fetch join errors
                    log.debug("ApplyJoins: Using regular join for ToOne path (count query): {}", path);
                    root.join(path, JoinType.LEFT);
                } else {
                    // For select queries, use fetch join to prevent N+1
                    log.debug("ApplyJoins: Using fetch join for ToOne path (select query): {}", path);
                    root.fetch(path, JoinType.LEFT);
                }
                log.debug("Successfully applied join for ToOne path: {}", path);
            } catch (Exception e) {
                log.warn("Join failed for ToOne path '{}', using regular join as fallback: {}", path, e.getMessage());
                root.join(path, JoinType.LEFT);
            }
        }

        // Apply ToMany joins (always regular join to prevent memory pagination)
        for (String path : toManyPaths) {
            try {
                log.debug("ApplyJoins: Using regular join for ToMany path: {}", path);
                root.join(path, JoinType.LEFT);
                log.debug("Successfully applied regular join for ToMany path: {}", path);
            } catch (Exception e) {
                log.warn("Join failed for ToMany path '{}': {}", path, e.getMessage());
            }
        }

        Set<String> finalToOneFields = relationshipAnalyzer.detectCommonToOneFields();
        finalToOneFields.removeAll(toOnePaths); // Remove already processed paths
        log.debug("Final ToOne fields to add: {}", finalToOneFields);

        // Apply additional ToOne fields for N+1 prevention (only for select queries)
        if (!isCountQuery) {
            for (String field : finalToOneFields) {
                try {
                    // Handle nested paths with safety validation
                    if (field.contains(".")) {
                        if (relationshipAnalyzer.isNestedPathSafeForJoin(root, field)) {
                            log.debug("ApplyJoins: Attempting fetch join for validated nested path: {}", field);
                            applyNestedFetchJoin(root, field);
                            log.debug("Successfully applied fetch join for nested path: {}", field);
                        } else {
                            log.debug("ApplyJoins: Nested path '{}' failed safety validation, skipping", field);
                        }
                        continue;
                    }

                    log.debug("ApplyJoins: Attempting fetch join for common ToOne field: {}", field);
                    root.fetch(field, JoinType.LEFT);
                    log.debug("Successfully applied fetch join for common ToOne field: {}", field);
                } catch (Exception e) {
                    log.warn("Fetch join failed for common ToOne field '{}', using regular join as fallback: {}", field, e.getMessage());
                    try {
                        root.join(field, JoinType.LEFT);
                    } catch (Exception joinException) {
                        log.warn("Regular join also failed for field '{}': {}", field, joinException.getMessage());
                    }
                }
            }
        }
    }

    /**
     * Apply smart fetch joins to avoid MultipleBagFetchException.
     * Strategy: Fetch only ToOne relationships and one primary ToMany relationship.
     * Other ToMany relationships will be loaded lazily or via batch fetching.
     */
    public void applySmartFetchJoins(Root<T> root, Set<String> paths) {
        log.debug("ApplySmartFetchJoins: Starting with paths: {}", paths);

        root.getJoins().clear();

        // Separate ToOne and ToMany paths
        Set<String> toOnePaths = new HashSet<>();
        Set<String> toManyPaths = new HashSet<>();

        for (String path : paths) {
            if (relationshipAnalyzer.isToManyPath(root, path)) {
                toManyPaths.add(path);
            } else {
                toOnePaths.add(path);
            }
        }

        log.debug("ApplySmartFetchJoins: ToOne paths from conditions: {}", toOnePaths);
        log.debug("ApplySmartFetchJoins: ToMany paths from conditions: {}", toManyPaths);

        // CRITICAL: Add commonly accessed ToOne relationships even if not in search conditions
        // This prevents N+1 problems for frequently accessed fields like 'position'
        Set<String> commonToOneFields = relationshipAnalyzer.detectCommonToOneFields();
        log.debug("ApplySmartFetchJoins: Common ToOne fields detected: {}", commonToOneFields);

        // Add all common ToOne fields to prevent N+1
        toOnePaths.addAll(commonToOneFields);
        log.debug("ApplySmartFetchJoins: Final ToOne paths to fetch: {}", toOnePaths);

        // Strategy 1: Always fetch join ALL ToOne relationships (safe and efficient)
        for (String path : toOnePaths) {
            try {
                log.debug("ApplySmartFetchJoins: Attempting fetch join for ToOne path: {}", path);
                root.fetch(path, JoinType.LEFT);
                log.info("ApplySmartFetchJoins: Successfully applied fetch join for ToOne: {}", path);
            } catch (Exception e) {
                log.warn("ApplySmartFetchJoins: Fetch join failed for path '{}', using regular join as fallback: {}", path, e.getMessage());
                // If fetch join fails, use regular join as fallback
                try {
                    root.join(path, JoinType.LEFT);
                    log.debug("ApplySmartFetchJoins: Successfully applied regular join fallback for: {}", path);
                } catch (Exception joinException) {
                    log.error("ApplySmartFetchJoins: Both fetch join and regular join failed for path '{}': {}", path, joinException.getMessage());
                }
            }
        }

        // Strategy 2: For ToMany relationships, use selective fetching
        if (toManyPaths.size() == 1) {
            // Single ToMany: Safe to fetch join
            String toManyPath = toManyPaths.iterator().next();
            log.debug("ApplySmartFetchJoins: Single ToMany path, applying fetch join: {}", toManyPath);
            try {
                root.fetch(toManyPath, JoinType.LEFT);
                log.info("ApplySmartFetchJoins: Successfully applied fetch join for single ToMany: {}", toManyPath);
            } catch (Exception e) {
                log.warn("ApplySmartFetchJoins: Fetch join failed for ToMany '{}', using regular join: {}", toManyPath, e.getMessage());
                root.join(toManyPath, JoinType.LEFT);
            }
        } else if (toManyPaths.size() > 1) {
            // Multiple ToMany: Select the most important one for fetch join
            String primaryToMany = selectPrimaryToManyForFetch(toManyPaths);
            log.debug("ApplySmartFetchJoins: Multiple ToMany paths, selected primary: {}", primaryToMany);
            if (primaryToMany != null) {
                try {
                    root.fetch(primaryToMany, JoinType.LEFT);
                    log.info("ApplySmartFetchJoins: Successfully applied fetch join for primary ToMany: {}", primaryToMany);
                } catch (Exception e) {
                    log.warn("ApplySmartFetchJoins: Fetch join failed for primary ToMany '{}': {}", primaryToMany, e.getMessage());
                }
            }

            // Other ToMany relationships: Use regular joins to enable lazy loading
            for (String path : toManyPaths) {
                if (!path.equals(primaryToMany)) {
                    log.debug("ApplySmartFetchJoins: Applying regular join for secondary ToMany: {}", path);
                    try {
                        root.join(path, JoinType.LEFT);
                        log.debug("ApplySmartFetchJoins: Successfully applied regular join for secondary ToMany: {}", path);
                    } catch (Exception e) {
                        log.error("ApplySmartFetchJoins: Regular join failed for secondary ToMany '{}': {}", path, e.getMessage());
                    }
                }
            }
        }

        log.info("ApplySmartFetchJoins: Completed join application - ToOne fetched: {}, ToMany fetched: {}",
                toOnePaths.size(), toManyPaths.isEmpty() ? 0 : 1);
    }

    /**
     * Apply only regular joins (no fetch joins) for filtering.
     */
    public void applyRegularJoinsOnly(Root<T> root, Set<String> paths) {
        root.getJoins().clear();

        for (String path : paths) {
            root.join(path, JoinType.LEFT);
        }
    }

    /**
     * Apply fetch joins for all relationships.
     */
    public void applyAllFetchJoins(Root<T> root, Set<String> paths) {
        root.getJoins().clear();

        for (String path : paths) {
            root.fetch(path, JoinType.LEFT);
        }
    }

    /**
     * Extract all field paths used in search conditions.
     */
    public Set<String> extractConditionPaths(List<SearchCondition.Node> nodes) {
        Set<String> conditionPaths = new HashSet<>();
        if (nodes == null) return conditionPaths;

        for (SearchCondition.Node node : nodes) {
            if (node instanceof SearchCondition.Condition) {
                SearchCondition.Condition condition = (SearchCondition.Condition) node;
                String entityField = condition.getEntityField();
                if (entityField != null && !entityField.isEmpty()) {
                    // Extract the relationship path (everything except the last field)
                    String[] pathParts = entityField.split("\\.");
                    if (pathParts.length > 1) {
                        StringBuilder path = new StringBuilder();
                        for (int i = 0; i < pathParts.length - 1; i++) {
                            if (path.length() > 0) {
                                path.append(".");
                            }
                            path.append(pathParts[i]);
                        }
                        conditionPaths.add(path.toString());
                    }
                }
            } else if (node instanceof SearchCondition.Group) {
                conditionPaths.addAll(extractConditionPaths(node.getNodes()));
            }
        }

        return conditionPaths;
    }

    /**
     * Select the most important ToMany relationship for fetch joining.
     * Priority: relationships used in search conditions > first alphabetically
     */
    private String selectPrimaryToManyForFetch(Set<String> toManyPaths) {
        if (toManyPaths.isEmpty()) {
            return null;
        }

        // If no path is used in conditions, return the first one alphabetically
        return toManyPaths.stream()
                .sorted()
                .findFirst()
                .orElse(null);
    }

    /**
     * Applies fetch join for nested paths by building the path step by step.
     */
    private void applyNestedFetchJoin(Root<T> root, String nestedPath) {
        try {
            String[] pathParts = nestedPath.split("\\.");
            From<?, ?> currentFrom = root;

            for (String part : pathParts) {
                // Check if this part is already fetched
                boolean alreadyFetched = currentFrom.getFetches().stream()
                        .anyMatch(fetch -> fetch.getAttribute().getName().equals(part));

                if (!alreadyFetched) {
                    currentFrom = (From<?, ?>) currentFrom.fetch(part, JoinType.LEFT);
                    log.debug("Applied fetch join for path part: {}", part);
                } else {
                    // Find existing fetch to continue the path
                    currentFrom = (From<?, ?>) currentFrom.getFetches().stream()
                            .filter(fetch -> fetch.getAttribute().getName().equals(part))
                            .findFirst()
                            .orElseThrow(() -> new IllegalStateException("Expected fetch not found"));
                    log.debug("Reusing existing fetch for path part: {}", part);
                }
            }

        } catch (Exception e) {
            log.warn("Failed to apply nested fetch join for path '{}': {}", nestedPath, e.getMessage());
            throw e;
        }
    }
} 