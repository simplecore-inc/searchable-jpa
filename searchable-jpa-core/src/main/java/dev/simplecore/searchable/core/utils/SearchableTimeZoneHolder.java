package dev.simplecore.searchable.core.utils;

import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Process-wide holder for the application timezone used to interpret timezone-less
 * search values and to expand date-only BETWEEN boundaries.
 *
 * <p>Defaults to {@link ZoneOffset#UTC} so search results never depend on the JVM
 * default timezone of the deployment host. The Spring Boot starter overrides this at
 * startup with the resolved application timezone (single canonical key — see
 * SearchableJpaConfiguration).
 *
 * <p>Timezone-less search values and date-only BETWEEN boundaries are interpreted in this
 * process-wide application zone, keeping search results independent of the deployment host's
 * JVM default timezone.
 */
public final class SearchableTimeZoneHolder {

    private static volatile ZoneId zoneId = ZoneOffset.UTC;

    private SearchableTimeZoneHolder() {
    }

    /**
     * Sets the application timezone. Called once during application startup.
     *
     * @param zone the application timezone; ignored if null
     */
    public static void setZoneId(ZoneId zone) {
        if (zone != null) {
            zoneId = zone;
        }
    }

    /**
     * Returns the application timezone used for timezone-less value interpretation.
     * Never returns null; defaults to UTC until configured.
     */
    public static ZoneId getZoneId() {
        return zoneId;
    }
}
