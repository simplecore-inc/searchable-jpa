package dev.simplecore.searchable.core.service;

import dev.simplecore.searchable.core.condition.SearchCondition;

import org.springframework.data.domain.Page;
import org.springframework.lang.NonNull;

import java.util.Optional;

/**
 * Mixin interface that provides {@link SearchableService} implementation via delegation.
 * <p>
 * Classes that cannot extend {@link DefaultSearchableService} (e.g., because they already
 * extend another class) can implement this interface instead. All {@link SearchableService}
 * methods are provided as default methods that delegate to a {@link SearchableServiceDelegate}.
 * <p>
 * Usage:
 * <pre>{@code
 * public class MyService extends SomeOtherBaseClass
 *         implements SearchableServiceSupport<MyEntity, Long> {
 *
 *     private final SearchableServiceDelegate<MyEntity, Long> searchableDelegate;
 *
 *     public MyService(JpaRepository<MyEntity, Long> repository, EntityManager em) {
 *         this.searchableDelegate = new SearchableServiceDelegate<>(repository, em, MyEntity.class);
 *     }
 *
 *     @Override
 *     public SearchableServiceDelegate<MyEntity, Long> getSearchableDelegate() {
 *         return searchableDelegate;
 *     }
 * }
 * }</pre>
 *
 * @param <T>  entity type
 * @param <ID> entity ID type
 * @see SearchableServiceDelegate
 * @see DefaultSearchableService
 */
public interface SearchableServiceSupport<T, ID> extends SearchableService<T> {

    /**
     * Returns the delegate that provides the actual SearchableService implementation.
     *
     * @return the searchable service delegate
     */
    SearchableServiceDelegate<T, ID> getSearchableDelegate();

    @Override
    @NonNull
    default Page<T> findAllWithSearch(@NonNull SearchCondition<?> searchCondition) {
        return getSearchableDelegate().findAllWithSearch(searchCondition);
    }

    @Override
    @NonNull
    default <D> Page<D> findAllWithSearch(@NonNull SearchCondition<?> searchCondition, Class<D> dtoClass) {
        return getSearchableDelegate().findAllWithSearch(searchCondition, dtoClass);
    }

    @Override
    @NonNull
    default Optional<T> findOneWithSearch(@NonNull SearchCondition<?> searchCondition) {
        return getSearchableDelegate().findOneWithSearch(searchCondition);
    }

    @Override
    @NonNull
    default Optional<T> findFirstWithSearch(@NonNull SearchCondition<?> searchCondition) {
        return getSearchableDelegate().findFirstWithSearch(searchCondition);
    }

    @Override
    default long deleteWithSearch(@NonNull SearchCondition<?> searchCondition) {
        return getSearchableDelegate().deleteWithSearch(searchCondition);
    }

    @Override
    default long countWithSearch(@NonNull SearchCondition<?> searchCondition) {
        return getSearchableDelegate().countWithSearch(searchCondition);
    }

    @Override
    default boolean existsWithSearch(@NonNull SearchCondition<?> searchCondition) {
        return getSearchableDelegate().existsWithSearch(searchCondition);
    }

    @Override
    default long updateWithSearch(@NonNull SearchCondition<?> searchCondition, @NonNull Object updateData) {
        return getSearchableDelegate().updateWithSearch(searchCondition, updateData);
    }
}
