package dev.simplecore.searchable.core.service.specification;

import lombok.extern.slf4j.Slf4j;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;
import java.util.HashSet;
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
     * Applies joins for the given paths, using fetch joins for ToOne relationships on select queries
     * and regular joins for ToMany relationships and count queries.
     *
     * <p>Nested paths (containing {@code .}) are applied segment by segment via
     * {@link #safelyApplyNestedJoin}, because JPA does not accept a dotted attribute name in
     * {@code join()}/{@code fetch()}. This prevents multi-level ToOne chains (e.g.
     * {@code department.manager}) from failing query construction.
     *
     * <p>Common ToOne fields are additionally fetch-joined on select queries to prevent N+1.
     * Nested paths that traverse a to-many collection are intentionally not fetch-joined here: they
     * are handled by the two-phase executor (Phase 2), which applies {@code distinct} and restores
     * order. Fetching a to-many collection in this path would corrupt single-result and count queries.
     */
    public void applyJoins(Root<T> root, Set<String> paths, boolean isCountQuery) {
        root.getJoins().clear();

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

        // ToOne paths.
        for (String path : toOnePaths) {
            if (path.contains(".")) {
                safelyApplyNestedJoin(root, path, !isCountQuery);
                continue;
            }
            try {
                if (isCountQuery) {
                    root.join(path, JoinType.LEFT);
                } else {
                    root.fetch(path, JoinType.LEFT);
                }
            } catch (Exception e) {
                log.warn("Join failed for ToOne path '{}', using regular join as fallback: {}", path, e.getMessage());
                try {
                    root.join(path, JoinType.LEFT);
                } catch (Exception fallbackException) {
                    log.warn("Regular join also failed for ToOne path '{}': {}", path, fallbackException.getMessage());
                }
            }
        }

        // ToMany paths: always a regular join to prevent in-memory pagination.
        for (String path : toManyPaths) {
            if (path.contains(".")) {
                safelyApplyNestedJoin(root, path, false);
                continue;
            }
            try {
                root.join(path, JoinType.LEFT);
            } catch (Exception e) {
                log.warn("Join failed for ToMany path '{}': {}", path, e.getMessage());
            }
        }

        // Additional common ToOne fields for N+1 prevention (select queries only).
        if (!isCountQuery) {
            Set<String> commonToOneFields = relationshipAnalyzer.detectCommonToOneFields();
            commonToOneFields.removeAll(toOnePaths);
            for (String field : commonToOneFields) {
                if (field.contains(".")) {
                    // Nested (through a to-many) fetch is handled by the two-phase executor, not here.
                    continue;
                }
                try {
                    root.fetch(field, JoinType.LEFT);
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
