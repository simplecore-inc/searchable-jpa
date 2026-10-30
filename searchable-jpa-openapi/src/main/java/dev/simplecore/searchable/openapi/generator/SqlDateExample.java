package dev.simplecore.searchable.openapi.generator;

import java.sql.Date;
import java.time.LocalDate;

/** Keeps JDBC date handling distinct from legacy timestamp-based Date examples. */
final class SqlDateExample {
    private SqlDateExample() { }
    static boolean supports(Class<?> type) { return type == Date.class; }
    static Date parse(String text) { return Date.valueOf(LocalDate.parse(text)); }
}
