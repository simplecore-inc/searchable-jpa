package dev.simplecore.searchable.test.support;

import org.hibernate.resource.jdbc.spi.StatementInspector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Hibernate {@link StatementInspector} used by performance/behavior tests to observe the exact SQL
 * Hibernate executes. Registered globally via
 * {@code spring.jpa.properties.hibernate.session_factory.statement_inspector} but inert until a test
 * calls {@link #start()}; it never rewrites SQL.
 *
 * <p>Tests use it to assert query counts (e.g. verifying N+1 is avoided) and to assert that specific
 * clauses such as {@code distinct} are present in the generated SQL.
 */
public class TestSqlCapture implements StatementInspector {

    private static final List<String> STATEMENTS = Collections.synchronizedList(new ArrayList<>());
    private static volatile boolean capturing = false;

    @Override
    public String inspect(String sql) {
        if (capturing && sql != null) {
            STATEMENTS.add(sql);
        }
        return sql;
    }

    /**
     * Begins capturing and clears any previously captured statements.
     */
    public static void start() {
        STATEMENTS.clear();
        capturing = true;
    }

    /**
     * Stops capturing. Already-captured statements remain available.
     */
    public static void stop() {
        capturing = false;
    }

    /**
     * Returns a snapshot of the captured SQL statements.
     */
    public static List<String> captured() {
        synchronized (STATEMENTS) {
            return new ArrayList<>(STATEMENTS);
        }
    }

    /**
     * Counts captured statements whose (lower-cased) text contains the given substring.
     */
    public static long countContaining(String needleLowerCase) {
        synchronized (STATEMENTS) {
            return STATEMENTS.stream()
                    .filter(sql -> sql.toLowerCase().contains(needleLowerCase))
                    .count();
        }
    }

    /**
     * Counts captured SELECT statements (statements whose trimmed text starts with {@code select}).
     */
    public static long countSelects() {
        synchronized (STATEMENTS) {
            return STATEMENTS.stream()
                    .filter(sql -> sql.trim().toLowerCase().startsWith("select"))
                    .count();
        }
    }

    public static void clear() {
        STATEMENTS.clear();
    }
}
