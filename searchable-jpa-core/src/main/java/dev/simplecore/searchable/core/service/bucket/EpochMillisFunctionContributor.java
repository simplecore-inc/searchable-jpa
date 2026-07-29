package dev.simplecore.searchable.core.service.bucket;

import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.FunctionContributor;
import org.hibernate.dialect.Dialect;
import org.hibernate.type.StandardBasicTypes;

/**
 * Teaches the query language to read an instant column as a plain count of milliseconds.
 *
 * <p>Dividing a window of time into slices needs the stored instant as a number, and there is no
 * portable way to ask for one. Hibernate's own temporal arithmetic is not it: on SQLite the JDBC
 * driver writes an instant as an integer of milliseconds while the dialect reads it back with
 * {@code strftime}, which answers null for a number — the two halves of the same stack disagree
 * about the column they share.
 *
 * <p>So the expression is registered here, once per database, and it is the whole of what this
 * library knows about any dialect. A database with no entry is not broken by its absence:
 * {@link TimeBucketCounter} falls back to a slower form that needs no such function.
 *
 * <p>Registered through {@code META-INF/services}, so a host application gets it by having the
 * library on the classpath and never wires it itself.
 *
 * @see TimeBucketCounter
 */
public class EpochMillisFunctionContributor implements FunctionContributor {

    /** The function a query calls to read an instant column as milliseconds since the epoch. */
    public static final String FUNCTION_NAME = "epoch_millis";

    @Override
    public void contributeFunctions(FunctionContributions functionContributions) {
        String pattern = pattern(functionContributions.getDialect());
        if (pattern == null) {
            return;
        }
        functionContributions.getFunctionRegistry().registerPattern(
                FUNCTION_NAME,
                pattern,
                functionContributions.getTypeConfiguration()
                        .getBasicTypeRegistry()
                        .resolve(StandardBasicTypes.LONG));
    }

    /**
     * @param dialect the database behind the host application
     * @return how that database turns an instant column into milliseconds since the epoch, or
     *         null when this library has no entry for it
     */
    private String pattern(Dialect dialect) {
        String name = dialect.getClass().getSimpleName().toLowerCase();

        // The driver already stores the instant as an integer of milliseconds, so the column is
        // itself the answer — a datetime function here would be reading a number as a date.
        if (name.contains("sqlite")) {
            return "?1";
        }
        if (name.contains("postgres") || name.contains("cockroach")) {
            return "cast(extract(epoch from ?1) * 1000 as bigint)";
        }
        if (name.contains("h2")) {
            return "datediff('MILLISECOND', timestamp '1970-01-01 00:00:00', ?1)";
        }
        if (name.contains("mysql") || name.contains("mariadb")) {
            return "(unix_timestamp(?1) * 1000)";
        }
        if (name.contains("oracle")) {
            return "(cast((cast(?1 as date) - date '1970-01-01') * 86400000 as number(19)))";
        }
        if (name.contains("sqlserver")) {
            return "datediff_big(millisecond, cast('1970-01-01' as datetime2), ?1)";
        }
        return null;
    }
}
