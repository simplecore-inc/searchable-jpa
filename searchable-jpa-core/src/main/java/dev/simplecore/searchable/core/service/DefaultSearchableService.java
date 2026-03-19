package dev.simplecore.searchable.core.service;

import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.service.specification.SearchableSpecificationBuilder;
import dev.simplecore.searchable.core.service.specification.SpecificationWithPageable;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import jakarta.persistence.EntityManager;
import java.lang.reflect.ParameterizedType;

/**
 * Default implementation of {@link SearchableService} that can be extended directly.
 * <p>
 * This class delegates all SearchableService operations to a {@link SearchableServiceDelegate}.
 * Existing subclasses continue to work without modification.
 * <p>
 * For classes that need to extend a different base class, use {@link SearchableServiceSupport}
 * interface with {@link SearchableServiceDelegate} instead.
 *
 * @param <T>  entity type
 * @param <ID> entity ID type
 * @see SearchableServiceSupport
 * @see SearchableServiceDelegate
 */
@NoRepositoryBean
public class DefaultSearchableService<T, ID> implements SearchableServiceSupport<T, ID> {

    protected final JpaRepository<T, ID> repository;
    private final SearchableServiceDelegate<T, ID> delegate;

    @SuppressWarnings("unchecked")
    public DefaultSearchableService(JpaRepository<T, ID> repository, EntityManager entityManager) {
        Class<T> entityClass = (Class<T>) ((ParameterizedType) getClass().getGenericSuperclass())
                .getActualTypeArguments()[0];
        this.repository = repository;
        this.delegate = new SearchableServiceDelegate<>(repository, entityManager, entityClass);
    }

    @Override
    public SearchableServiceDelegate<T, ID> getSearchableDelegate() {
        return delegate;
    }

    protected SpecificationWithPageable<T> createSpecification(SearchCondition<?> searchCondition) {
        return delegate.createSpecification(searchCondition);
    }

    protected SearchableSpecificationBuilder<T> createSpecificationBuilder(SearchCondition<?> searchCondition) {
        return delegate.createSpecificationBuilder(searchCondition);
    }
}
