package dev.simplecore.searchable.core.service;

import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.exception.SearchableConfigurationException;
import dev.simplecore.searchable.core.exception.SearchableOperationException;
import dev.simplecore.searchable.core.service.specification.SearchableSpecificationBuilder;
import dev.simplecore.searchable.core.service.specification.SpecificationWithPageable;
import dev.simplecore.searchable.core.utils.SearchableFieldUtils;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Id;
import jakarta.persistence.Version;
import jakarta.persistence.metamodel.SingularAttribute;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.modelmapper.ModelMapper;
import org.modelmapper.config.Configuration;
import org.modelmapper.convention.MatchingStrategies;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.lang.NonNull;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.ReflectionUtils;

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
                .setFieldAccessLevel(Configuration.AccessLevel.PRIVATE)
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
        Objects.requireNonNull(repository, "repository must not be null");
        Objects.requireNonNull(entityManager, "entityManager must not be null");
        Objects.requireNonNull(entityClass, "entityClass must not be null");
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
        return readInTransaction(builder::buildAndExecuteWithTwoPhaseOptimization);
    }

    @Override
    @NonNull
    public <P> Page<P> findAllWithSearch(@NonNull SearchCondition<?> searchCondition, Class<P> projectionClass) {
        if (!projectionClass.isInterface()) {
            throw new SearchableConfigurationException("Projection class must be an interface");
        }

        SearchableSpecificationBuilder<T> builder = createSpecificationBuilder(searchCondition);
        Page<T> entityPage = readInTransaction(builder::buildAndExecuteWithTwoPhaseOptimization);

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
        SearchCondition<?> firstCondition = new SearchCondition<>(searchCondition.getNodes(), 0, 1);
        firstCondition.setSort(searchCondition.getSort());
        firstCondition.setFetchFields(searchCondition.getFetchFields());
        SearchableSpecificationBuilder<T> builder = createSpecificationBuilder(firstCondition);
        return readInTransaction(builder::buildAndExecuteFirst);
    }

    @Override
    public long deleteWithSearch(@NonNull SearchCondition<?> searchCondition) {
        return writeInTransaction(owned -> new WriteQueryExecutor<>(entityManager, entityClass, specificationExecutor)
                .execute(searchCondition, repository::delete, owned));
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

    @Override
    public long updateWithSearch(@NonNull SearchCondition<?> searchCondition, @NonNull Object updateData) {
        // Retain ModelMapper conversion, but copy only properties present in the original
        // payload. Mapping a narrow DTO must not reset unrelated primitive entity fields.
        Object mappedData = entityClass.isInstance(updateData) ? updateData : MODEL_MAPPER.map(updateData, entityClass);
        return writeInTransaction(owned -> new WriteQueryExecutor<>(entityManager, entityClass, specificationExecutor)
                .execute(searchCondition, entity -> copyNonNullProperties(mappedData, updateData, entity), owned));
    }

    private <R> R readInTransaction(Supplier<R> work) {
        if (entityManager.isJoinedToTransaction()) return work.get();
        var transaction = new TransactionTemplate(new JpaTransactionManager(entityManager.getEntityManagerFactory()));
        transaction.setReadOnly(true);
        return transaction.execute(status -> work.get());
    }

    private long writeInTransaction(Function<Boolean, Long> work) {
        if (entityManager.isJoinedToTransaction()) return work.apply(false);
        var manager = new JpaTransactionManager(entityManager.getEntityManagerFactory());
        Long result = new TransactionTemplate(manager)
                .execute(status -> work.apply(true));
        return result == null ? 0 : result;
    }

    /**
     * Copies supplied, non-null mutable properties through the entity hierarchy.
     * IDs, versions, audit-managed fields, non-updatable fields and collections are excluded.
     */
    private void copyNonNullProperties(Object source, Object payload, T target) {
        for (Class<?> current = entityClass; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                    continue;
                }
                if (isProtectedField(field) || !isProvided(payload, field.getName())) continue;
                if (Collection.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                field.setAccessible(true);
                try {
                    Object value = field.get(source);
                    if (value != null) {
                        field.set(target, value);
                    }
                } catch (IllegalAccessException e) {
                    throw new SearchableOperationException(
                            "Failed to copy field '" + field.getName() + "' during batch update", e);
                }
            }
        }
    }
    private boolean isProvided(Object payload, String name) {
        if (payload instanceof Map<?, ?> map) return map.containsKey(name) && map.get(name) != null;
        var field = SearchableFieldUtils.findFieldInHierarchy(payload.getClass(), name);
        if (field != null) {
            ReflectionUtils.makeAccessible(field);
            return ReflectionUtils.getField(field, payload) != null;
        }
        var wrapper = new BeanWrapperImpl(payload);
        return wrapper.isReadableProperty(name) && wrapper.getPropertyValue(name) != null;
    }

    private boolean isProtectedField(Field field) {
        if (Modifier.isFinal(field.getModifiers())) return true;
        try {
            var attribute = entityManager.getMetamodel().entity(entityClass).getAttribute(field.getName());
            if (attribute instanceof SingularAttribute<?, ?> singular &&
                    (singular.isId() || singular.isVersion())) return true;
            if (protectedMember(attribute.getJavaMember())) return true;
        } catch (IllegalArgumentException e) {
            return true; // Not a persistent field.
        }
        return protectedMember(field);
    }

    private boolean protectedMember(Member member) {
        if (!(member instanceof AnnotatedElement element)) return false;
        Column column = element.getAnnotation(Column.class);
        if (column != null && !column.updatable()) return true;
        return element.isAnnotationPresent(Id.class)
                || element.isAnnotationPresent(EmbeddedId.class)
                || element.isAnnotationPresent(Version.class)
                || element.isAnnotationPresent(CreatedDate.class)
                || element.isAnnotationPresent(CreatedBy.class)
                || element.isAnnotationPresent(LastModifiedDate.class)
                || element.isAnnotationPresent(LastModifiedBy.class)
                || element.isAnnotationPresent(CreationTimestamp.class)
                || element.isAnnotationPresent(UpdateTimestamp.class);
    }
}
