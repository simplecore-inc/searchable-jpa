package dev.simplecore.searchable.core.service.specification;

import dev.simplecore.searchable.core.condition.SearchCondition;
import lombok.extern.slf4j.Slf4j;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Manages JOIN strategies for optimal query performance.
 * Handles ToOne vs ToMany relationship optimization and prevents N+1 problems.
 */
@Slf4j
public class JoinStrategyManager<T> {

    @SuppressWarnings("unused")
    private final EntityManager entityManager;
    @SuppressWarnings("unused")
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

        log.trace("Detected ToOne paths: {}", toOnePaths);
        log.trace("Detected ToMany paths: {}", toManyPaths);

        // Apply ToOne joins
        for (String path : toOnePaths) {
            try {
                if (isCountQuery) {
                    // For count queries, use regular join to avoid fetch join errors
                    log.trace("ApplyJoins: Using regular join for ToOne path (count query): {}", path);
                    root.join(path, JoinType.LEFT);
                } else {
                    // For select queries, use fetch join to prevent N+1
                    log.trace("ApplyJoins: Using fetch join for ToOne path (select query): {}", path);
                    root.fetch(path, JoinType.LEFT);
                }
                log.trace("Successfully applied join for ToOne path: {}", path);
            } catch (Exception e) {
                log.warn("Join failed for ToOne path '{}', using regular join as fallback: {}", path, e.getMessage());
                root.join(path, JoinType.LEFT);
            }
        }

        // Apply ToMany joins (always regular join to prevent memory pagination)
        for (String path : toManyPaths) {
            try {
                log.trace("ApplyJoins: Using regular join for ToMany path: {}", path);
                root.join(path, JoinType.LEFT);
                log.trace("Successfully applied regular join for ToMany path: {}", path);
            } catch (Exception e) {
                log.warn("Join failed for ToMany path '{}': {}", path, e.getMessage());
            }
        }

        Set<String> finalToOneFields = relationshipAnalyzer.detectCommonToOneFields();
        finalToOneFields.removeAll(toOnePaths); // Remove already processed paths
        log.trace("Final ToOne fields to add: {}", finalToOneFields);

        // Apply additional ToOne fields for N+1 prevention (only for select queries)
        if (!isCountQuery) {
            for (String field : finalToOneFields) {
                try {
                    // Handle nested paths with safety validation
                    if (field.contains(".")) {
                        if (relationshipAnalyzer.isNestedPathSafeForJoin(root, field)) {
                            log.trace("ApplyJoins: Attempting safe nested join for validated path: {}", field);
                            if (safelyApplyNestedJoin(root, field, true)) {
                                log.trace("Successfully applied safe nested join for path: {}", field);
                            } else {
                                log.trace("Safe nested join failed for path: {}", field);
                            }
                        } else {
                            log.trace("ApplyJoins: Nested path '{}' failed safety validation, skipping", field);
                        }
                        continue;
                    }

                    log.trace("ApplyJoins: Attempting fetch join for common ToOne field: {}", field);
                    root.fetch(field, JoinType.LEFT);
                    log.trace("Successfully applied fetch join for common ToOne field: {}", field);
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
        log.trace("ApplySmartFetchJoins: Starting with paths: {}", paths);

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

        log.trace("ApplySmartFetchJoins: ToOne paths from conditions: {}", toOnePaths);
        log.trace("ApplySmartFetchJoins: ToMany paths from conditions: {}", toManyPaths);

        // CRITICAL: Add commonly accessed ToOne relationships even if not in search conditions
        // This prevents N+1 problems for frequently accessed fields like 'position'
        Set<String> commonToOneFields = relationshipAnalyzer.detectCommonToOneFields();
        log.trace("ApplySmartFetchJoins: Common ToOne fields detected: {}", commonToOneFields);

        // Add all common ToOne fields to prevent N+1
        toOnePaths.addAll(commonToOneFields);
        log.trace("ApplySmartFetchJoins: Final ToOne paths to fetch: {}", toOnePaths);

        // Strategy 1: Always fetch join ALL ToOne relationships (safe and efficient)
        for (String path : toOnePaths) {
            try {
                log.trace("ApplySmartFetchJoins: Attempting fetch join for ToOne path: {}", path);
                root.fetch(path, JoinType.LEFT);
                log.info("ApplySmartFetchJoins: Successfully applied fetch join for ToOne: {}", path);
            } catch (Exception e) {
                log.warn("ApplySmartFetchJoins: Fetch join failed for path '{}', using regular join as fallback: {}", path, e.getMessage());
                // If fetch join fails, use regular join as fallback
                try {
                    root.join(path, JoinType.LEFT);
                    log.trace("ApplySmartFetchJoins: Successfully applied regular join fallback for: {}", path);
                } catch (Exception joinException) {
                    log.error("ApplySmartFetchJoins: Both fetch join and regular join failed for path '{}': {}", path, joinException.getMessage());
                }
            }
        }

        // Strategy 2: For ToMany relationships, use selective fetching
        if (toManyPaths.size() == 1) {
            // Single ToMany: Safe to fetch join
            String toManyPath = toManyPaths.iterator().next();
            log.trace("ApplySmartFetchJoins: Single ToMany path, applying fetch join: {}", toManyPath);
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
            log.trace("ApplySmartFetchJoins: Multiple ToMany paths, selected primary: {}", primaryToMany);
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
                    log.trace("ApplySmartFetchJoins: Applying regular join for secondary ToMany: {}", path);
                    try {
                        root.join(path, JoinType.LEFT);
                        log.trace("ApplySmartFetchJoins: Successfully applied regular join for secondary ToMany: {}", path);
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
     * Uses safe path validation to prevent "Unable to locate Attribute" errors.
     */
    public void applyRegularJoinsOnly(Root<T> root, Set<String> paths) {
        root.getJoins().clear();

        for (String path : paths) {
            // Validate path before attempting to join
            if (relationshipAnalyzer.isValidPath(root, path)) {
                try {
                    // Try to apply nested join safely
                    if (path.contains(".")) {
                        safelyApplyNestedJoin(root, path, false);
                    } else {
                        root.join(path, JoinType.LEFT);
                    }
                    log.trace("ApplyRegularJoinsOnly: Successfully applied join for path: {}", path);
                } catch (Exception e) {
                    log.warn("ApplyRegularJoinsOnly: Failed to apply join for path '{}': {}", path, e.getMessage());
                }
            } else {
                log.warn("ApplyRegularJoinsOnly: Invalid path '{}' skipped during join application", path);
            }
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
    @SuppressWarnings("unused")
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
                    log.trace("Applied fetch join for path part: {}", part);
                } else {
                    // Find existing fetch to continue the path
                    currentFrom = (From<?, ?>) currentFrom.getFetches().stream()
                            .filter(fetch -> fetch.getAttribute().getName().equals(part))
                            .findFirst()
                            .orElseThrow(() -> new IllegalStateException("Expected fetch not found"));
                    log.trace("Reusing existing fetch for path part: {}", part);
                }
            }

        } catch (Exception e) {
            log.warn("Failed to apply nested fetch join for path '{}': {}", nestedPath, e.getMessage());
            throw e;
        }
    }

    /**
     * Safely applies nested joins by validating each path segment.
     * This prevents "Unable to locate Attribute" errors by checking path validity.
     */
    private boolean safelyApplyNestedJoin(Root<T> root, String nestedPath, boolean useFetchJoin) {
        try {
            String[] pathParts = nestedPath.split("\\.");
            From<?, ?> currentFrom = root;
            
            // Validate the complete path first
            if (!relationshipAnalyzer.isValidPath(root, nestedPath)) {
                log.trace("Path validation failed for nested path: {}", nestedPath);
                return false;
            }

            // Apply joins step by step
            StringBuilder currentPath = new StringBuilder();
            for (int i = 0; i < pathParts.length; i++) {
                String part = pathParts[i];
                if (currentPath.length() > 0) {
                    currentPath.append(".");
                }
                currentPath.append(part);
                
                // Check if this join already exists
                boolean alreadyJoined = currentFrom.getJoins().stream()
                        .anyMatch(join -> join.getAttribute().getName().equals(part));
                        
                if (!alreadyJoined) {
                    if (useFetchJoin && i == pathParts.length - 1) {
                        // Only use fetch join for the final part if requested
                        currentFrom = (From<?, ?>) currentFrom.fetch(part, JoinType.LEFT);
                        log.trace("Applied fetch join for final path part: {}", currentPath);
                    } else {
                        // Use regular join for intermediate parts
                        currentFrom = (From<?, ?>) currentFrom.join(part, JoinType.LEFT);
                        log.trace("Applied regular join for path part: {}", currentPath);
                    }
                } else {
                    // Find existing join to continue the path
                    Join<?, ?> existingJoin = currentFrom.getJoins().stream()
                            .filter(join -> join.getAttribute().getName().equals(part))
                            .findFirst()
                            .orElse(null);
                    if (existingJoin != null) {
                        currentFrom = (From<?, ?>) existingJoin;
                        log.trace("Reusing existing join for path part: {}", currentPath);
                    }
                }
            }
            
            return true;
        } catch (Exception e) {
            log.warn("Failed to safely apply nested join for path '{}': {}", nestedPath, e.getMessage());
            return false;
        }
    }
} 