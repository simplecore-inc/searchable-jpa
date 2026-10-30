package dev.simplecore.searchable.core.utils;

/**
 * Process-wide setting for the pattern operators (CONTAINS, STARTS_WITH, ENDS_WITH and their
 * negations).
 *
 * <p>Off by default, so a pattern matches as the database compares strings. When on, both the column
 * and the pattern are lower-cased, so "pro" finds "Pro" on a database whose comparison is
 * case-sensitive, such as PostgreSQL. An index serving such a search is built on {@code lower(column)}.
 * The Spring Boot starter sets it at startup from {@code searchable.pattern-matching.case-insensitive}.
 */
public final class SearchablePatternMatchingHolder {

    private static volatile boolean caseInsensitive = false;

    private SearchablePatternMatchingHolder() {
    }

    /**
     * @param value whether pattern operators ignore case
     */
    public static void setCaseInsensitive(boolean value) {
        caseInsensitive = value;
    }

    /**
     * @return whether pattern operators ignore case
     */
    public static boolean isCaseInsensitive() {
        return caseInsensitive;
    }
}
