package dev.simplecore.searchable.core.service;

import dev.simplecore.searchable.core.condition.SearchCondition;
import org.springframework.data.domain.Page;
import org.springframework.lang.NonNull;

import javax.persistence.NonUniqueResultException;
import java.util.Optional;

@SuppressWarnings({"unused"})
public interface SearchableService<T> {
    /**
     * Search entities with given search conditions
     *
     * @param searchCondition search conditions
     * @return page of matching entities
     */
    @NonNull
    Page<T> findAllWithSearch(@NonNull SearchCondition<?> searchCondition);

    @NonNull
    <D> Page<D> findAllWithSearch(@NonNull SearchCondition<?> searchCondition, Class<D> dtoClass);

    /**
     * Find a single entity matching the search conditions
     * Throws an exception if more than one entity is found
     *
     * @param searchCondition search conditions
     * @return the entity matching the conditions
     * @throws NonUniqueResultException if more than one entity is found
     */
    @NonNull
    Optional<T> findOneWithSearch(@NonNull SearchCondition<?> searchCondition);

    /**
     * Find the first entity matching the search conditions
     *
     * @param searchCondition search conditions
     * @return the first entity matching the conditions
     */
    @NonNull
    Optional<T> findFirstWithSearch(@NonNull SearchCondition<?> searchCondition);

    /**
     * Delete entities matching the search conditions
     *
     * @param searchCondition search conditions
     * @return number of deleted entities
     */
    long deleteWithSearch(@NonNull SearchCondition<?> searchCondition);

    /**
     * Count entities matching the search conditions
     *
     * @param searchCondition search conditions
     * @return number of matching entities
     */
    long countWithSearch(@NonNull SearchCondition<?> searchCondition);

    /**
     * Check if any entities match the search conditions
     *
     * @param searchCondition search conditions
     * @return true if any entities match, false otherwise
     */
    boolean existsWithSearch(@NonNull SearchCondition<?> searchCondition);

    long updateWithSearch(@NonNull SearchCondition<?> searchCondition, @NonNull Object updateData);

}