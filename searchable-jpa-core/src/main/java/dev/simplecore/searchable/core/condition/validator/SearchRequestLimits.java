package dev.simplecore.searchable.core.condition.validator;

import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.exception.SearchableValidationException;
import jakarta.persistence.EntityManager;
import java.util.Collection;
import java.util.List;

/** Resource limits checked independently of DTO validation, including server-built conditions. */
public final class SearchRequestLimits {
    public static final int MAX_NODES = 1000;
    public static final int MAX_DEPTH = 32;
    public static final int MAX_IN_VALUES = 1000;
    public static final int MAX_SORT_FIELDS = 16;
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int DEFAULT_MAX_PAGE_SIZE = 1000;

    private SearchRequestLimits() { }

    public static void validateStructure(SearchCondition<?> condition) {
        visit(condition.getNodes(), 0, new int[1]);
        if (condition.getSort() != null && condition.getSort().getOrders().size() > MAX_SORT_FIELDS) {
            fail("At most " + MAX_SORT_FIELDS + " sort fields are allowed");
        }
    }

    private static void visit(List<SearchCondition.Node> nodes, int depth, int[] count) {
        if (depth > MAX_DEPTH) fail("Search groups exceed maximum depth " + MAX_DEPTH);
        if (nodes == null) fail("Search nodes must not be null");
        for (SearchCondition.Node node : nodes) {
            if (++count[0] > MAX_NODES) fail("Search exceeds maximum node count " + MAX_NODES);
            if (node instanceof SearchCondition.Group group) {
                visit(group.getNodes(), depth + 1, count);
            } else if (node instanceof SearchCondition.Condition leaf) {
                if (leaf.getValue() instanceof Collection<?> values && values.size() > MAX_IN_VALUES) {
                    fail("Search collection exceeds maximum size " + MAX_IN_VALUES);
                }
            } else {
                fail("Search node must be a condition or group");
            }
        }
    }

    public static int defaultPageSize(EntityManager em) {
        return setting(em, "default_page_size", DEFAULT_PAGE_SIZE);
    }

    public static void validatePage(EntityManager em, int page, int size) {
        int maximum = setting(em, "max_page_size", DEFAULT_MAX_PAGE_SIZE);
        if (page < 0 || size < 1 || size > maximum) fail("Page must be nonnegative and size must be between 1 and " + maximum);
        if ((long) page * size > Integer.MAX_VALUE) fail("Page offset exceeds the JPA integer limit");
    }

    private static int setting(EntityManager em, String key, int fallback) {
        Object value = em.getEntityManagerFactory().getProperties().get("searchable." + key);
        return value == null ? fallback : Integer.parseInt(value.toString());
    }

    private static void fail(String message) {
        throw new SearchableValidationException(message);
    }
}
