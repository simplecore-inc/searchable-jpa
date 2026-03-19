package dev.simplecore.searchable.core.service;

import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.exception.SearchableConfigurationException;
import dev.simplecore.searchable.core.service.specification.SearchableSpecificationBuilder;
import dev.simplecore.searchable.core.service.specification.SpecificationWithPageable;

import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;

import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.lang.NonNull;

import jakarta.persistence.EntityManager;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Standalone delegate that encapsulates all SearchableService implementation logic.
 * <p>
 * This class can be used in two ways:
 * <ul>
 *   <li>Directly, by creating an instance and calling methods on it</li>
 *   <li>Via {@link SearchableServiceSupport} interface, which provides default methods
 *       that delegate to an instance of this class</li>
 * </ul>
 *
 * @param <T>  entity type
 * @param <ID> entity ID type
 * @see SearchableServiceSupport
 * @see DefaultSearchableService
 */
public class SearchableServiceDelegate<T, ID> implements SearchableService<T> {

    private static final ModelMapper MODEL_MAPPER;
    static {
        MODEL_MAPPER = new ModelMapper();
        MODEL_MAPPER.getConfiguration()
                .setSkipNullEnabled(true)
                .setFieldMatchingEnabled(true)
                .setFieldAccessLevel(org.modelmapper.config.Configuration.AccessLevel.PRIVATE)
                .setPropertyCondition(ctx -> ctx.getSource() != null)
                .setMatchingStrategy(MatchingStrategies.STRICT);
    }

    private final JpaRepository<T, ID> repository;
    private final JpaSpecificationExecutor<T> specificationExecutor;
    private final Class<T> entityClass;
    private final EntityManager entityManager;
    private final ProjectionFactory projectionFactory = new SpelAwareProxyProjectionFactory();

    @SuppressWarnings("unchecked")
    public SearchableServiceDelegate(JpaRepository<T, ID> repository, EntityManager entityManager, Class<T> entityClass) {
        if (!(repository instanceof JpaSpecificationExecutor<?>)) {
            throw new SearchableConfigurationException("Repository must implement JpaSpecificationExecutor");
        }
        this.repository = repository;
        this.specificationExecutor = (JpaSpecificationExecutor<T>) repository;
        this.entityClass = entityClass;
        this.entityManager = entityManager;
    }

    public SpecificationWithPageable<T> createSpecification(SearchCondition<?> searchCondition) {
        return SearchableSpecificationBuilder.of(searchCondition, entityManager, entityClass, specificationExecutor).buildSpecificationOnly();
    }

    public SearchableSpecificationBuilder<T> createSpecificationBuilder(SearchCondition<?> searchCondition) {
        return SearchableSpecificationBuilder.of(searchCondition, entityManager, entityClass, specificationExecutor);
    }

    @Override
    @NonNull
    public Page<T> findAllWithSearch(@NonNull SearchCondition<?> searchCondition) {
        SearchableSpecificationBuilder<T> builder = createSpecificationBuilder(searchCondition);
        return builder.buildAndExecuteWithTwoPhaseOptimization();
    }

    @Override
    @NonNull
    public <P> Page<P> findAllWithSearch(@NonNull SearchCondition<?> searchCondition, Class<P> projectionClass) {
        if (!projectionClass.isInterface()) {
            throw new SearchableConfigurationException("Projection class must be an interface");
        }

        SearchableSpecificationBuilder<T> builder = createSpecificationBuilder(searchCondition);
        Page<T> entityPage = builder.buildAndExecuteWithTwoPhaseOptimization();

        return entityPage.map(entity -> projectionFactory.createProjection(projectionClass, entity));
    }

    @Override
    @NonNull
    public Optional<T> findOneWithSearch(@NonNull SearchCondition<?> searchCondition) {
        SpecificationWithPageable<T> spec = createSpecification(searchCondition);
        return specificationExecutor.findOne(spec.getSpecification());
    }

    @Override
    @NonNull
    public Optional<T> findFirstWithSearch(@NonNull SearchCondition<?> searchCondition) {
        SearchableSpecificationBuilder<T> builder = createSpecificationBuilder(searchCondition);
        Page<T> firstPage = builder.buildAndExecuteWithTwoPhaseOptimization();
        return firstPage.getContent().stream().findFirst();
    }

    @Override
    public long deleteWithSearch(@NonNull SearchCondition<?> searchCondition) {
        SpecificationWithPageable<T> spec = createSpecification(searchCondition);
        List<T> toDelete = specificationExecutor.findAll(spec.getSpecification());
        repository.deleteAll(toDelete);
        return toDelete.size();
    }

    @Override
    public long countWithSearch(@NonNull SearchCondition<?> searchCondition) {
        SpecificationWithPageable<T> spec = createSpecification(searchCondition);
        return specificationExecutor.count(spec.getSpecification());
    }

    @Override
    public boolean existsWithSearch(@NonNull SearchCondition<?> searchCondition) {
        SpecificationWithPageable<T> spec = createSpecification(searchCondition);
        return specificationExecutor.exists(spec.getSpecification());
    }

    @SuppressWarnings("unchecked")
    @Override
    public long updateWithSearch(@NonNull SearchCondition<?> searchCondition, @NonNull Object updateData) {

        if (updateData.getClass() != entityClass) {
            updateData = MODEL_MAPPER.map(updateData, entityClass);
        }

        try {
            SpecificationWithPageable<T> specification = createSpecification(searchCondition);
            List<T> entitiesToUpdate = specificationExecutor.findAll(specification.getSpecification());

            for (T entity : entitiesToUpdate) {
                ID id = (ID) entityClass.getMethod("getId").invoke(entity);

                T managedEntity = repository.findById(id)
                        .orElseThrow(() -> new SearchableConfigurationException("Entity not found with id: " + id));

                copyNonNullProperties(updateData, managedEntity);

                repository.save(managedEntity);
            }

            return entitiesToUpdate.size();
        } catch (Exception e) {
            throw new SearchableConfigurationException("Failed to perform batch update", e);
        }
    }

    private void copyNonNullProperties(Object source, T target) throws IllegalAccessException {
        Field[] fields = entityClass.getDeclaredFields();
        for (Field field : fields) {
            field.setAccessible(true);
            Object value = field.get(source);
            if (value != null && !Collection.class.isAssignableFrom(field.getType())) {
                field.set(target, value);
            }
        }
    }
}
