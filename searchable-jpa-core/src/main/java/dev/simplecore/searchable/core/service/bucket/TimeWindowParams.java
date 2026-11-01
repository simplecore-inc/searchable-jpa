package dev.simplecore.searchable.core.service.bucket;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The three parameters that describe a time window rather than filter by one.
 *
 * <p>A counts endpoint takes the list's own search parameters as a map, and the window it is
 * asked about arrives in the same query string. Those three are not fields of anything, so the
 * search parser would refuse them; they are removed here, in one place, before the remaining
 * parameters narrow the rows {@link TimeBucketCounter} counts.
 */
public final class TimeWindowParams {

    /** The first instant in the window, inclusive. */
    public static final String FROM = "from";

    /** The instant the window ends at, exclusive. */
    public static final String TO = "to";

    /** How many slices to divide the window into. */
    public static final String BUCKETS = "buckets";

    private static final List<String> NAMES = List.of(FROM, TO, BUCKETS);

    private TimeWindowParams() {
    }

    /**
     * @param params what the caller sent, or null
     * @return the same parameters without the window ones, leaving only what filters rows
     */
    public static Map<String, String> strip(Map<String, String> params) {
        if (params == null) {
            return Map.of();
        }
        Map<String, String> filters = new LinkedHashMap<>(params);
        NAMES.forEach(filters::remove);
        return filters;
    }
}
