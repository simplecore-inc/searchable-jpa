package dev.simplecore.searchable.core.service.join;

import dev.simplecore.searchable.core.exception.SearchableJoinException;
import lombok.NonNull;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.*;
import java.util.Objects;

/**
 * Manages JPA join operations for search conditions.
 * Thread-safe implementation for handling joins required by search conditions.
 *
 * @param <T> The root entity type
 */
public class JoinManager<T> {

    private final Root<T> root;
    private final EntityManager entityManager;
    private final Class<T> entityClass;

    /**
     * Creates a new JoinManager instance.
     *
     * @param entityManager The JPA EntityManager
     * @param root          The root entity
     * @throws IllegalStateException if the EntityManager is not open
     */
    @SuppressWarnings("unchecked")
    public JoinManager(@NonNull EntityManager entityManager, @NonNull Root<T> root) {
        validateEntityManager(entityManager);
        this.root = root;
        this.entityManager = entityManager;
        this.entityClass = (Class<T>) root.getJavaType();
    }

    /**
     * Gets the entity class for this JoinManager.
     *
     * @return The entity class
     */
    public Class<T> getEntityClass() {
        return entityClass;
    }

    /**
     * Gets the EntityManager for this JoinManager.
     *
     * @return The EntityManager
     */
    public EntityManager getEntityManager() {
        return entityManager;
    }

    /**
     * Validates the entity manager state.
     */
    private void validateEntityManager(EntityManager entityManager) {
        Objects.requireNonNull(entityManager, "EntityManager must not be null");
        if (!entityManager.isOpen()) {
            throw new SearchableJoinException("EntityManager must be open");
        }
    }

    /**
     * Validates the join path.
     */
    private void validatePath(String path) {
        if (path == null || path.trim().isEmpty()) {
            throw new SearchableJoinException("Path must not be null or empty");
        }
    }


    /**
     * Gets or creates a join path for the given field path.
     *
     * @param path The field path (e.g., "author.name")
     * @return The Path object for the field
     */
    public Path<?> getJoinPath(String path) {
        validatePath(path);

        String[] pathParts = path.split("\\.");
        From<?, ?> current = root;

        // Exclude the last part as it's a field name
        for (int i = 0; i < pathParts.length - 1; i++) {
            current = getOrCreateJoin(current, pathParts[i]);
        }

        return current.get(pathParts[pathParts.length - 1]);
    }

    private Join<?, ?> getOrCreateJoin(From<?, ?> from, String attribute) {
        // Check existing joins
        for (Join<?, ?> join : from.getJoins()) {
            if (join.getAttribute().getName().equals(attribute)) {
                return join;
            }
        }

        // Create new join if not exists
        return from.join(attribute, JoinType.LEFT);
    }

} 