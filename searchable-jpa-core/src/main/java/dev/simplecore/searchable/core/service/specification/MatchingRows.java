package dev.simplecore.searchable.core.service.specification;

import dev.simplecore.searchable.core.condition.SearchCondition;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.Optional;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Narrows an aggregate query to the rows a list's {@link SearchCondition} matches.
 *
 * <p>The condition is applied inside an {@code EXISTS} over a second root rather than to the
 * aggregate's own root. A condition on a joined collection would otherwise multiply each entity
 * by its matching children, and every count and sum read over it would be inflated.
 */
public final class MatchingRows {

    private MatchingRows() {
    }

    /**
     * @param cb the criteria builder of the aggregate query
     * @param query the aggregate query the subquery is attached to
     * @param root the aggregate query's root
     * @param entityType the entity being aggregated
     * @param repository the entity's repository, which the condition is compiled against
     * @param entityManager where the condition's metadata is read
     * @param condition what the list is currently filtered by
     * @param <T> the entity type
     * @return the predicate, or empty when the condition matches every row
     */
    public static <T> Optional<Predicate> of(CriteriaBuilder cb, CriteriaQuery<?> query, Root<T> root,
                                             Class<T> entityType, JpaSpecificationExecutor<T> repository,
                                             EntityManager entityManager, SearchCondition<?> condition) {
        Specification<T> filter = SearchableSpecificationBuilder
                .of(condition, entityManager, entityType, repository)
                .buildSpecificationOnly()
                .getSpecification();
        Subquery<Integer> matching = query.subquery(Integer.class);
        Root<T> filtered = matching.from(entityType);
        Predicate filterPredicate = filter.toPredicate(filtered, cb.createQuery(Object.class), cb);
        if (filterPredicate == null) {
            return Optional.empty();
        }
        matching.select(cb.literal(1)).where(cb.equal(filtered, root), filterPredicate);
        return Optional.of(cb.exists(matching));
    }
}
