package dev.simplecore.searchable.core.service;

import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.exception.SearchableConfigurationException;
import dev.simplecore.searchable.core.service.specification.SearchableSpecificationBuilder;
import dev.simplecore.searchable.core.service.specification.SpecificationWithPageable;

import org.springframework.core.GenericTypeResolver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import jakarta.persistence.EntityManager;

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
        // Resolve the entity type by walking the class hierarchy, so subclasses that extend through
        // an intermediate (possibly abstract) class are supported and do not fail with a ClassCastException.
        Class<?>[] typeArguments = GenericTypeResolver.resolveTypeArguments(getClass(), DefaultSearchableService.class);
        if (typeArguments == null || typeArguments.length < 1 || typeArguments[0] == null) {
            throw new SearchableConfigurationException(
                    "Unable to resolve the entity type for " + getClass().getName()
                            + ". Ensure the concrete service binds the entity type argument of DefaultSearchableService.");
        }
        Class<T> entityClass = (Class<T>) typeArguments[0];
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
