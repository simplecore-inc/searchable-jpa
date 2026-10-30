package dev.simplecore.searchable.core.service;

import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.service.specification.SearchableSpecificationBuilder;
import dev.simplecore.searchable.core.utils.SearchableFieldUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.*;
import java.util.*;
import java.util.function.Consumer;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.util.ReflectionUtils;

/** Visits matching rows in primary-key order, without OFFSET skips when writes change the filter. */
final class WriteQueryExecutor<T> {
    private static final int CHUNK_SIZE = 200;
    private final EntityManager em;
    private final Class<T> type;
    private final JpaSpecificationExecutor<T> repository;

    WriteQueryExecutor(EntityManager em, Class<T> type, JpaSpecificationExecutor<T> repository) {
        this.em = em;
        this.type = type;
        this.repository = repository;
    }

    long execute(SearchCondition<?> condition, Consumer<T> action, boolean releaseEntities) {
        List<String> keys = keyPaths();
        List<Object> cursor = null;
        long count = 0;
        while (true) {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<T> query = cb.createQuery(type);
            Root<T> root = query.from(type);
            // Reuse predicates without entity fetch joins so pagination always happens in SQL.
            CriteriaQuery<Object> filterQuery = cb.createQuery(Object.class);
            var filter = SearchableSpecificationBuilder.of(condition, em, type, repository)
                    .buildSpecificationOnly().getSpecification();
            Predicate predicate = filter.toPredicate(root, filterQuery, cb);
            List<Predicate> where = new ArrayList<>();
            if (predicate != null) where.add(predicate);
            if (cursor != null) where.add(after(cb, root, keys, cursor));
            query.select(root).distinct(true).where(where.toArray(new Predicate[0]));
            query.orderBy(keys.stream().map(key -> cb.asc(path(root, key))).toList());
            List<T> batch = em.createQuery(query).setMaxResults(CHUNK_SIZE).getResultList();
            if (batch.isEmpty()) return count;
            // Capture the last key before callbacks mutate or remove the entities.
            cursor = readKey(batch.get(batch.size() - 1), keys);
            for (T entity : batch) { action.accept(entity); count++; }
            em.flush();
            // Never detach objects owned by a caller's pre-existing transaction.
            if (releaseEntities) for (T entity : batch) em.detach(entity);
        }
    }

    private List<String> keyPaths() {
        String key = SearchableFieldUtils.getPrimaryKeyFieldName(em, type);
        if (!SearchableFieldUtils.COMPOSITE_KEY_MARKER.equals(key)) return List.of(key);
        String embedded = SearchableFieldUtils.getEmbeddedIdAttributeName(em, type);
        return SearchableFieldUtils.getCompositeKeyFieldNames(em, type).stream()
                .map(name -> embedded == null ? name : embedded + "." + name).toList();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Predicate after(CriteriaBuilder cb, Root<T> root, List<String> keys, List<Object> cursor) {
        List<Predicate> alternatives = new ArrayList<>();
        for (int i = 0; i < keys.size(); i++) {
            List<Predicate> prefix = new ArrayList<>();
            for (int j = 0; j < i; j++) prefix.add(cb.equal(path(root, keys.get(j)), cursor.get(j)));
            prefix.add(cb.greaterThan((Expression) path(root, keys.get(i)), (Comparable) cursor.get(i)));
            alternatives.add(cb.and(prefix.toArray(new Predicate[0])));
        }
        return cb.or(alternatives.toArray(new Predicate[0]));
    }

    private Path<?> path(Path<?> root, String name) {
        Path<?> path = root;
        for (String part : name.split("\\.")) path = path.get(part);
        return path;
    }

    private List<Object> readKey(T entity, List<String> keys) {
        var wrapper = new BeanWrapperImpl(entity);
        List<Object> result = new ArrayList<>();
        for (String key : keys) {
            Object value = entity;
            if (wrapper.isReadableProperty(key)) value = wrapper.getPropertyValue(key);
            else for (String part : key.split("\\.")) {
                var field = SearchableFieldUtils.findFieldInHierarchy(value.getClass(), part);
                if (field == null) throw new IllegalStateException("Cannot read identifier " + key);
                ReflectionUtils.makeAccessible(field);
                value = ReflectionUtils.getField(field, value);
            }
            result.add(value);
        }
        return result;
    }
}
