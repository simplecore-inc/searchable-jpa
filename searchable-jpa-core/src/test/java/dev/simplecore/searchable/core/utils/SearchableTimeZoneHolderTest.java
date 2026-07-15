package dev.simplecore.searchable.core.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link SearchableValueParser} interprets timezone-less values and expands
 * date-only BETWEEN boundaries using the zone configured in {@link SearchableTimeZoneHolder},
 * not the JVM default timezone.
 */
class SearchableTimeZoneHolderTest {

    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

    private ZoneId previous;

    @BeforeEach
    void capture() {
        previous = SearchableTimeZoneHolder.getZoneId();
    }

    @AfterEach
    void restore() {
        SearchableTimeZoneHolder.setZoneId(previous);
    }

    @Test
    @DisplayName("null zone is ignored and does not overwrite the current zone")
    void nullZoneIsIgnored() {
        SearchableTimeZoneHolder.setZoneId(NEW_YORK);
        SearchableTimeZoneHolder.setZoneId(null);
        assertThat(SearchableTimeZoneHolder.getZoneId()).isEqualTo(NEW_YORK);
    }

    @Test
    @DisplayName("timezone-less Instant value is parsed in the holder's configured zone")
    void zonelessInstantUsesHolderZone() {
        SearchableTimeZoneHolder.setZoneId(NEW_YORK);

        Instant parsed = (Instant) SearchableValueParser.parseValue("2024-01-15T00:00:00", Instant.class);

        Instant expected = LocalDateTime.of(2024, 1, 15, 0, 0, 0).atZone(NEW_YORK).toInstant();
        assertThat(parsed).isEqualTo(expected);
    }

    @Test
    @DisplayName("date-only BETWEEN boundaries are expanded in the holder's configured zone")
    void dateOnlyBetweenUsesHolderZone() {
        SearchableTimeZoneHolder.setZoneId(NEW_YORK);

        Instant start = (Instant) SearchableValueParser.parseValueForBetween("2024-01-15", Instant.class, false);
        Instant end = (Instant) SearchableValueParser.parseValueForBetween("2024-01-15", Instant.class, true);

        Instant expectedStart = LocalDate.of(2024, 1, 15).atStartOfDay(NEW_YORK).toInstant();
        Instant expectedEnd = LocalDate.of(2024, 1, 15).atTime(LocalTime.MAX).atZone(NEW_YORK).toInstant();
        assertThat(start).isEqualTo(expectedStart);
        assertThat(end).isEqualTo(expectedEnd);
    }

    @Test
    @DisplayName("changing the holder zone changes the resulting instant for the same date-only value")
    void changingZoneChangesResult() {
        SearchableTimeZoneHolder.setZoneId(ZoneOffset.UTC);
        Instant utcStart = (Instant) SearchableValueParser.parseValueForBetween("2024-01-15", Instant.class, false);

        SearchableTimeZoneHolder.setZoneId(NEW_YORK);
        Instant nyStart = (Instant) SearchableValueParser.parseValueForBetween("2024-01-15", Instant.class, false);

        // New York is behind UTC, so its local midnight is a strictly later instant.
        assertThat(utcStart).isEqualTo(LocalDate.of(2024, 1, 15).atStartOfDay(ZoneOffset.UTC).toInstant());
        assertThat(nyStart).isEqualTo(LocalDate.of(2024, 1, 15).atStartOfDay(NEW_YORK).toInstant());
        assertThat(nyStart).isAfter(utcStart);
    }
}
