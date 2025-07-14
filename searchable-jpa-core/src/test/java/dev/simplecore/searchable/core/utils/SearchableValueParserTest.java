package dev.simplecore.searchable.core.utils;

import dev.simplecore.searchable.core.exception.SearchableParseException;
import dev.simplecore.searchable.test.config.BaseTestConfig;
import dev.simplecore.searchable.test.config.TestConfig;
import dev.simplecore.searchable.test.dto.TestPostDTOs;
import dev.simplecore.searchable.test.enums.TestPostStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.*;
import java.util.Date;
import java.util.Locale;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ContextConfiguration(classes = {BaseTestConfig.class, TestConfig.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@Transactional
class SearchableValueParserTest {

    @BeforeEach
    void setUp() {
        // Set English locale for tests to ensure consistent error messages
        LocaleContextHolder.setLocale(Locale.ENGLISH);
    }

    @Test
    @DisplayName("String values should be parsed correctly")
    void testStringParsing() {
        // Given & When & Then
        assertThat(SearchableValueParser.parseValue("hello", String.class)).isEqualTo("hello");
        assertThat(SearchableValueParser.parseValue("", String.class)).isNull(); // Empty string returns null
        assertThat(SearchableValueParser.parseValue("   ", String.class)).isNull(); // Whitespace returns null
        assertThat(SearchableValueParser.parseValue("null", String.class)).isNull();
        assertThat(SearchableValueParser.parseValue(null, String.class)).isNull();
    }

    @Test
    @DisplayName("Boolean values should be parsed correctly")
    void testBooleanParsing() {
        // True values
        assertThat((Boolean) SearchableValueParser.parseValue("true", Boolean.class)).isTrue();
        assertThat((Boolean) SearchableValueParser.parseValue("TRUE", Boolean.class)).isTrue();
        assertThat((Boolean) SearchableValueParser.parseValue("1", Boolean.class)).isTrue();
        assertThat((Boolean) SearchableValueParser.parseValue("yes", Boolean.class)).isTrue();
        assertThat((Boolean) SearchableValueParser.parseValue("Y", Boolean.class)).isTrue();
        assertThat((Boolean) SearchableValueParser.parseValue("on", Boolean.class)).isTrue();

        // False values
        assertThat((Boolean) SearchableValueParser.parseValue("false", Boolean.class)).isFalse();
        assertThat((Boolean) SearchableValueParser.parseValue("FALSE", Boolean.class)).isFalse();
        assertThat((Boolean) SearchableValueParser.parseValue("0", Boolean.class)).isFalse();
        assertThat((Boolean) SearchableValueParser.parseValue("no", Boolean.class)).isFalse();
        assertThat((Boolean) SearchableValueParser.parseValue("N", Boolean.class)).isFalse();
        assertThat((Boolean) SearchableValueParser.parseValue("off", Boolean.class)).isFalse();

        // Primitive boolean
        assertThat((Boolean) SearchableValueParser.parseValue("true", boolean.class)).isTrue();
        assertThat((Boolean) SearchableValueParser.parseValue("false", boolean.class)).isFalse();

        // Invalid boolean
        assertThatThrownBy(() -> SearchableValueParser.parseValue("invalid", Boolean.class))
            .isInstanceOf(SearchableParseException.class)
            .hasMessageContaining("Invalid boolean value");
    }

    @Test
    @DisplayName("Character values should be parsed correctly")
    void testCharacterParsing() {
        // Valid character
        assertThat(SearchableValueParser.parseValue("A", Character.class)).isEqualTo('A');
        assertThat(SearchableValueParser.parseValue("1", char.class)).isEqualTo('1');

        // Invalid character length
        assertThatThrownBy(() -> SearchableValueParser.parseValue("AB", Character.class))
            .isInstanceOf(SearchableParseException.class)
            .hasMessageContaining("exactly one character long");

        // Empty string returns null, so won't reach character parsing
        assertThat(SearchableValueParser.parseValue("", Character.class)).isNull();
    }

    @Test
    @DisplayName("Integer values should be parsed correctly")
    void testIntegerParsing() {
        // Valid integers
        assertThat(SearchableValueParser.parseValue("123", Integer.class)).isEqualTo(123);
        assertThat(SearchableValueParser.parseValue("-456", int.class)).isEqualTo(-456);
        assertThat(SearchableValueParser.parseValue("0", Integer.class)).isEqualTo(0);

        // With grouping separators
        assertThat(SearchableValueParser.parseValue("1,000", Integer.class)).isEqualTo(1000);
        assertThat(SearchableValueParser.parseValue("1 000", Integer.class)).isEqualTo(1000);

        // Invalid integers
        assertThatThrownBy(() -> SearchableValueParser.parseValue("abc", Integer.class))
            .isInstanceOf(SearchableParseException.class);

        assertThatThrownBy(() -> SearchableValueParser.parseValue("123.45", Integer.class))
            .isInstanceOf(SearchableParseException.class);
    }

    @Test
    @DisplayName("Long values should be parsed correctly")
    void testLongParsing() {
        assertThat(SearchableValueParser.parseValue("9223372036854775807", Long.class))
            .isEqualTo(Long.MAX_VALUE);
        assertThat(SearchableValueParser.parseValue("-9223372036854775808", long.class))
            .isEqualTo(Long.MIN_VALUE);
    }

    @Test
    @DisplayName("Float and Double values should be parsed correctly")
    void testFloatingPointParsing() {
        // Float
        assertThat(SearchableValueParser.parseValue("123.45", Float.class)).isEqualTo(123.45f);
        assertThat(SearchableValueParser.parseValue("-67.89", float.class)).isEqualTo(-67.89f);

        // Double
        assertThat(SearchableValueParser.parseValue("123.456789", Double.class)).isEqualTo(123.456789);
        assertThat(SearchableValueParser.parseValue("-987.654321", double.class)).isEqualTo(-987.654321);

        // Scientific notation
        assertThat(SearchableValueParser.parseValue("1.23E+10", Double.class)).isEqualTo(1.23E+10);
    }

    @Test
    @DisplayName("BigDecimal and BigInteger should be parsed correctly")
    void testBigNumberParsing() {
        // BigDecimal
        BigDecimal expectedDecimal = new BigDecimal("123456789.987654321");
        assertThat(SearchableValueParser.parseValue("123456789.987654321", BigDecimal.class))
            .isEqualTo(expectedDecimal);

        // BigInteger
        BigInteger expectedInteger = new BigInteger("12345678901234567890");
        assertThat(SearchableValueParser.parseValue("12345678901234567890", BigInteger.class))
            .isEqualTo(expectedInteger);
    }

    @Test
    @DisplayName("Enum values should be parsed correctly")
    void testEnumParsing() {
        // Exact match
        assertThat(SearchableValueParser.parseValue("PUBLISHED", TestPostStatus.class))
            .isEqualTo(TestPostStatus.PUBLISHED);

        // Case insensitive
        assertThat(SearchableValueParser.parseValue("draft", TestPostStatus.class))
            .isEqualTo(TestPostStatus.DRAFT);
        assertThat(SearchableValueParser.parseValue("ARCHIVED", TestPostStatus.class))
            .isEqualTo(TestPostStatus.ARCHIVED);

        // Invalid enum value
        assertThatThrownBy(() -> SearchableValueParser.parseValue("INVALID", TestPostStatus.class))
            .isInstanceOf(SearchableParseException.class)
            .hasMessageContaining("Invalid enum value");
    }

    @Test
    @DisplayName("LocalDateTime should be parsed correctly")
    void testLocalDateTimeParsing() {
        // ISO format
        LocalDateTime expected = LocalDateTime.of(2023, 12, 25, 15, 30, 45);
        assertThat(SearchableValueParser.parseValue("2023-12-25T15:30:45", LocalDateTime.class))
            .isEqualTo(expected);

        // Various formats
        assertThat(SearchableValueParser.parseValue("2023-12-25 15:30:45", LocalDateTime.class))
            .isEqualTo(expected);
        assertThat(SearchableValueParser.parseValue("2023/12/25 15:30:45", LocalDateTime.class))
            .isEqualTo(expected);

        // Date only (should append midnight)
        assertThat(SearchableValueParser.parseValue("2023-12-25", LocalDateTime.class))
            .isEqualTo(LocalDateTime.of(2023, 12, 25, 0, 0, 0));

        // Invalid format
        assertThatThrownBy(() -> SearchableValueParser.parseValue("invalid-date", LocalDateTime.class))
            .isInstanceOf(SearchableParseException.class);
    }

    @Test
    @DisplayName("LocalDateTime should parse ISO 8601 UTC format correctly")
    void testLocalDateTimeParsingWithUtcFormat() {
        // Note: These tests use Asia/Seoul timezone (UTC+9)
        // UTC times are converted to local system timezone
        
        // ISO 8601 UTC format with milliseconds - 00:00:00Z becomes 09:00:00 in Asia/Seoul
        LocalDateTime expected = LocalDateTime.of(2024, 12, 31, 9, 0, 0);
        assertThat(SearchableValueParser.parseValue("2024-12-31T00:00:00.000Z", LocalDateTime.class))
            .isEqualTo(expected);

        // ISO 8601 UTC format without milliseconds
        assertThat(SearchableValueParser.parseValue("2024-12-31T00:00:00Z", LocalDateTime.class))
            .isEqualTo(expected);

        // ISO 8601 with different UTC time - 15:30:45Z becomes 00:30:45+1day in Asia/Seoul
        LocalDateTime expectedWithTime = LocalDateTime.of(2025, 1, 1, 0, 30, 45, 123_000_000);
        assertThat(SearchableValueParser.parseValue("2024-12-31T15:30:45.123Z", LocalDateTime.class))
            .isEqualTo(expectedWithTime);

        // ISO 8601 with timezone offset (UTC+0) - 15:30:45+00:00 becomes 00:30:45+1day in Asia/Seoul
        LocalDateTime expectedWithOffset = LocalDateTime.of(2025, 1, 1, 0, 30, 45);
        assertThat(SearchableValueParser.parseValue("2024-12-31T15:30:45+00:00", LocalDateTime.class))
            .isEqualTo(expectedWithOffset);

        // ISO 8601 with positive timezone offset (UTC+5) - 20:30:45+05:00 becomes 00:30:45+1day in Asia/Seoul
        LocalDateTime expectedWithPositiveOffset = LocalDateTime.of(2025, 1, 1, 0, 30, 45);
        assertThat(SearchableValueParser.parseValue("2024-12-31T20:30:45+05:00", LocalDateTime.class))
            .isEqualTo(expectedWithPositiveOffset);
    }

    @Test
    @DisplayName("LocalDateTime should parse various timezone formats correctly")
    void testLocalDateTimeParsingWithVariousTimezoneFormats() {
        // Note: These tests use Asia/Seoul timezone (UTC+9)
        
        // ISO 8601 with negative timezone offset (UTC-5) - 10:30:45-05:00 becomes 00:30:45+1day in Asia/Seoul
        LocalDateTime expectedWithNegativeOffset = LocalDateTime.of(2025, 1, 1, 0, 30, 45);
        assertThat(SearchableValueParser.parseValue("2024-12-31T10:30:45-05:00", LocalDateTime.class))
            .isEqualTo(expectedWithNegativeOffset);

        // ISO 8601 with microseconds and timezone - 15:30:45.123456Z becomes 00:30:45.123456+1day in Asia/Seoul
        LocalDateTime expectedWithMicroseconds = LocalDateTime.of(2025, 1, 1, 0, 30, 45, 123456000);
        assertThat(SearchableValueParser.parseValue("2024-12-31T15:30:45.123456Z", LocalDateTime.class))
            .isEqualTo(expectedWithMicroseconds);

        // ISO 8601 with nanoseconds and timezone - 15:30:45.123456789Z becomes 00:30:45.123456789+1day in Asia/Seoul
        LocalDateTime expectedWithNanoseconds = LocalDateTime.of(2025, 1, 1, 0, 30, 45, 123456789);
        assertThat(SearchableValueParser.parseValue("2024-12-31T15:30:45.123456789Z", LocalDateTime.class))
            .isEqualTo(expectedWithNanoseconds);

        // Alternative timezone format without colon - 20:30:45+0500 becomes 00:30:45+1day in Asia/Seoul
        LocalDateTime expectedWithoutColon = LocalDateTime.of(2025, 1, 1, 0, 30, 45);
        assertThat(SearchableValueParser.parseValue("2024-12-31T20:30:45+0500", LocalDateTime.class))
            .isEqualTo(expectedWithoutColon);
    }

    @Test
    @DisplayName("LocalDateTime should parse additional common API formats correctly")
    void testLocalDateTimeParsingWithAdditionalFormats() {
        // Note: These tests use Asia/Seoul timezone (UTC+9)
        
        // Common API formats with literal 'Z' - 00:30:45Z becomes 09:30:45 in Asia/Seoul
        LocalDateTime expectedLiteralZ = LocalDateTime.of(2024, 12, 31, 9, 30, 45);
        assertThat(SearchableValueParser.parseValue("2024-12-31T00:30:45Z", LocalDateTime.class))
            .isEqualTo(expectedLiteralZ);

        // Alternative separators with timezone - space separator
        LocalDateTime expectedSpaceSeparator = LocalDateTime.of(2024, 12, 31, 9, 30, 45);
        assertThat(SearchableValueParser.parseValue("2024-12-31 00:30:45+00:00", LocalDateTime.class))
            .isEqualTo(expectedSpaceSeparator);

        // Single digit fractional seconds - 00:30:45.1Z becomes 09:30:45.1 in Asia/Seoul
        LocalDateTime expectedSingleFraction = LocalDateTime.of(2024, 12, 31, 9, 30, 45, 100_000_000);
        assertThat(SearchableValueParser.parseValue("2024-12-31T00:30:45.1Z", LocalDateTime.class))
            .isEqualTo(expectedSingleFraction);

        // Double digit fractional seconds - 00:30:45.12Z becomes 09:30:45.12 in Asia/Seoul
        LocalDateTime expectedDoubleFraction = LocalDateTime.of(2024, 12, 31, 9, 30, 45, 120_000_000);
        assertThat(SearchableValueParser.parseValue("2024-12-31T00:30:45.12Z", LocalDateTime.class))
            .isEqualTo(expectedDoubleFraction);

        // Additional fractional seconds variations - 3 digits
        LocalDateTime expectedTripleFraction = LocalDateTime.of(2024, 12, 31, 9, 30, 45, 123_000_000);
        assertThat(SearchableValueParser.parseValue("2024-12-31T00:30:45.123Z", LocalDateTime.class))
            .isEqualTo(expectedTripleFraction);
    }

    @Test
    @DisplayName("LocalDateTime should parse date-only values correctly for between operations")
    void testLocalDateTimeParsingForBetween() {
        // Date-only input for start value (should be 00:00:00)
        LocalDateTime expectedStart = LocalDateTime.of(2024, 12, 31, 0, 0, 0);
        assertThat(SearchableValueParser.parseValueForBetween("2024-12-31", LocalDateTime.class, false))
            .isEqualTo(expectedStart);

        // Date-only input for end value (should be 23:59:59.999999999)
        LocalDateTime expectedEnd = LocalDateTime.of(2024, 12, 31, 23, 59, 59, 999_999_999);
        assertThat(SearchableValueParser.parseValueForBetween("2024-12-31", LocalDateTime.class, true))
            .isEqualTo(expectedEnd);

        // DateTime input should remain unchanged
        LocalDateTime expectedDateTime = LocalDateTime.of(2024, 12, 31, 15, 30, 45);
        assertThat(SearchableValueParser.parseValueForBetween("2024-12-31T15:30:45", LocalDateTime.class, false))
            .isEqualTo(expectedDateTime);
        assertThat(SearchableValueParser.parseValueForBetween("2024-12-31T15:30:45", LocalDateTime.class, true))
            .isEqualTo(expectedDateTime);

        // Various date formats
        assertThat(SearchableValueParser.parseValueForBetween("2024/12/31", LocalDateTime.class, false))
            .isEqualTo(expectedStart);
        assertThat(SearchableValueParser.parseValueForBetween("31-12-2024", LocalDateTime.class, false))
            .isEqualTo(expectedStart);
        assertThat(SearchableValueParser.parseValueForBetween("20241231", LocalDateTime.class, false))
            .isEqualTo(expectedStart);
    }

    @Test
    @DisplayName("Date should parse date-only values correctly for between operations")
    void testDateParsingForBetween() {
        // Date-only input for start value (should be 00:00:00)
        LocalDateTime expectedStart = LocalDateTime.of(2024, 12, 31, 0, 0, 0);
        Date expectedStartDate = Date.from(expectedStart.atZone(ZoneId.systemDefault()).toInstant());
        assertThat(SearchableValueParser.parseValueForBetween("2024-12-31", Date.class, false))
            .isEqualTo(expectedStartDate);

        // Date-only input for end value (should be 23:59:59.999999999)
        LocalDateTime expectedEnd = LocalDateTime.of(2024, 12, 31, 23, 59, 59, 999_999_999);
        Date expectedEndDate = Date.from(expectedEnd.atZone(ZoneId.systemDefault()).toInstant());
        assertThat(SearchableValueParser.parseValueForBetween("2024-12-31", Date.class, true))
            .isEqualTo(expectedEndDate);
    }

    @Test
    @DisplayName("Instant should parse date-only values correctly for between operations")
    void testInstantParsingForBetween() {
        // Date-only input for start value (should be 00:00:00)
        LocalDateTime expectedStart = LocalDateTime.of(2024, 12, 31, 0, 0, 0);
        Instant expectedStartInstant = expectedStart.atZone(ZoneId.systemDefault()).toInstant();
        assertThat(SearchableValueParser.parseValueForBetween("2024-12-31", Instant.class, false))
            .isEqualTo(expectedStartInstant);

        // Date-only input for end value (should be 23:59:59.999999999)
        LocalDateTime expectedEnd = LocalDateTime.of(2024, 12, 31, 23, 59, 59, 999_999_999);
        Instant expectedEndInstant = expectedEnd.atZone(ZoneId.systemDefault()).toInstant();
        assertThat(SearchableValueParser.parseValueForBetween("2024-12-31", Instant.class, true))
            .isEqualTo(expectedEndInstant);
    }

    @Test
    @DisplayName("isDateOnly should correctly identify date-only values")
    void testIsDateOnlyDetection() {
        // This is a private method, so we test it indirectly through parseValueForBetween
        
        // Date-only values should be adjusted
        LocalDateTime dateOnlyStart = LocalDateTime.of(2024, 12, 31, 0, 0, 0);
        LocalDateTime dateOnlyEnd = LocalDateTime.of(2024, 12, 31, 23, 59, 59, 999_999_999);
        
        assertThat(SearchableValueParser.parseValueForBetween("2024-12-31", LocalDateTime.class, false))
            .isEqualTo(dateOnlyStart);
        assertThat(SearchableValueParser.parseValueForBetween("2024-12-31", LocalDateTime.class, true))
            .isEqualTo(dateOnlyEnd);
        
        // DateTime values should not be adjusted
        LocalDateTime dateTimeValue = LocalDateTime.of(2024, 12, 31, 15, 30, 45);
        assertThat(SearchableValueParser.parseValueForBetween("2024-12-31T15:30:45", LocalDateTime.class, false))
            .isEqualTo(dateTimeValue);
        assertThat(SearchableValueParser.parseValueForBetween("2024-12-31T15:30:45", LocalDateTime.class, true))
            .isEqualTo(dateTimeValue);
    }

    @Test
    @DisplayName("LocalDate should be parsed correctly")
    void testLocalDateParsing() {
        LocalDate expected = LocalDate.of(2023, 12, 25);

        // Various formats
        assertThat(SearchableValueParser.parseValue("2023-12-25", LocalDate.class)).isEqualTo(expected);
        assertThat(SearchableValueParser.parseValue("2023/12/25", LocalDate.class)).isEqualTo(expected);
        assertThat(SearchableValueParser.parseValue("25-12-2023", LocalDate.class)).isEqualTo(expected);
        assertThat(SearchableValueParser.parseValue("20231225", LocalDate.class)).isEqualTo(expected);

        // Test the specific format that was failing
        LocalDate expected2024 = LocalDate.of(2024, 12, 31);
        assertThat(SearchableValueParser.parseValue("31-12-2024", LocalDate.class)).isEqualTo(expected2024);

        // Invalid format
        assertThatThrownBy(() -> SearchableValueParser.parseValue("invalid-date", LocalDate.class))
            .isInstanceOf(SearchableParseException.class);
    }

    @Test
    @DisplayName("LocalTime should be parsed correctly")
    void testLocalTimeParsing() {
        LocalTime expected = LocalTime.of(15, 30, 45);

        // Various formats
        assertThat(SearchableValueParser.parseValue("15:30:45", LocalTime.class)).isEqualTo(expected);
        assertThat(SearchableValueParser.parseValue("153045", LocalTime.class)).isEqualTo(expected);

        // With milliseconds
        assertThat(SearchableValueParser.parseValue("15:30:45.123", LocalTime.class))
            .isEqualTo(LocalTime.of(15, 30, 45, 123_000_000));

        // Invalid format
        assertThatThrownBy(() -> SearchableValueParser.parseValue("invalid-time", LocalTime.class))
            .isInstanceOf(SearchableParseException.class);
    }

    @Test
    @DisplayName("ZonedDateTime should be parsed correctly")
    void testZonedDateTimeParsing() {
        // With timezone
        ZonedDateTime result = (ZonedDateTime) SearchableValueParser.parseValue(
            "2023-12-25T15:30:45+09:00", ZonedDateTime.class);
        assertThat(result.getYear()).isEqualTo(2023);
        assertThat(result.getMonthValue()).isEqualTo(12);
        assertThat(result.getDayOfMonth()).isEqualTo(25);

        // Without timezone (should use system default)
        ZonedDateTime resultWithoutTz = (ZonedDateTime) SearchableValueParser.parseValue(
            "2023-12-25T15:30:45", ZonedDateTime.class);
        assertThat(resultWithoutTz.getZone()).isEqualTo(ZoneId.systemDefault());
    }

    @Test
    @DisplayName("OffsetDateTime should be parsed correctly")
    void testOffsetDateTimeParsing() {
        // With offset
        OffsetDateTime result = (OffsetDateTime) SearchableValueParser.parseValue(
            "2023-12-25T15:30:45+09:00", OffsetDateTime.class);
        assertThat(result.getYear()).isEqualTo(2023);
        assertThat(result.getOffset()).isEqualTo(ZoneOffset.of("+09:00"));

        // Without offset (should use system default)
        OffsetDateTime resultWithoutOffset = (OffsetDateTime) SearchableValueParser.parseValue(
            "2023-12-25T15:30:45", OffsetDateTime.class);
        assertThat(resultWithoutOffset.getOffset()).isNotNull();
    }

    @Test
    @DisplayName("Instant should be parsed correctly")
    void testInstantParsing() {
        // ISO instant format
        Instant result = (Instant) SearchableValueParser.parseValue(
            "2023-12-25T15:30:45Z", Instant.class);
        assertThat(result).isNotNull();

        // Local datetime (should convert to instant)
        Instant resultFromLocal = (Instant) SearchableValueParser.parseValue(
            "2023-12-25T15:30:45", Instant.class);
        assertThat(resultFromLocal).isNotNull();
    }

    @Test
    @DisplayName("Date should be parsed correctly")
    void testDateParsing() {
        // Various formats should work
        Date result = (Date) SearchableValueParser.parseValue("2023-12-25T15:30:45Z", Date.class);
        assertThat(result).isNotNull();

        Date resultFromLocal = (Date) SearchableValueParser.parseValue("2023-12-25T15:30:45", Date.class);
        assertThat(resultFromLocal).isNotNull();
    }

    @Test
    @DisplayName("Year and YearMonth should be parsed correctly")
    void testTemporalTypeParsing() {
        // Year
        assertThat(SearchableValueParser.parseValue("2023", Year.class)).isEqualTo(Year.of(2023));

        // YearMonth
        assertThat(SearchableValueParser.parseValue("2023-12", YearMonth.class))
            .isEqualTo(YearMonth.of(2023, 12));
    }

    @Test
    @DisplayName("Null and empty values should be handled correctly")
    void testNullAndEmptyValues() {
        // Null input
        assertThat(SearchableValueParser.parseValue(null, String.class)).isNull();
        assertThat(SearchableValueParser.parseValue(null, Integer.class)).isNull();

        // Empty string
        assertThat(SearchableValueParser.parseValue("", String.class)).isNull();
        assertThat(SearchableValueParser.parseValue("   ", String.class)).isNull();

        // "null" string
        assertThat(SearchableValueParser.parseValue("null", String.class)).isNull();
        assertThat(SearchableValueParser.parseValue("NULL", Integer.class)).isNull();
    }

    @Test
    @DisplayName("Unicode and special characters should be handled correctly")
    void testUnicodeAndSpecialCharacters() {
        // Unicode normalization
        assertThat(SearchableValueParser.parseValue("café", String.class)).isEqualTo("café");

        // BOM removal
        String withBom = "\uFEFF" + "test";
        assertThat(SearchableValueParser.parseValue(withBom, String.class)).isEqualTo("test");

        // Special characters in numbers
        assertThat(SearchableValueParser.parseValue("1,234", Integer.class)).isEqualTo(1234);
        assertThat(SearchableValueParser.parseValue("1 234", Integer.class)).isEqualTo(1234);
    }

    @Test
    @DisplayName("Edge cases and error conditions should be handled correctly")
    void testEdgeCasesAndErrors() {
        // Unsupported type - Object.class returns as String
        assertThat(SearchableValueParser.parseValue("test", Object.class)).isEqualTo("test");

        // Number overflow
        assertThatThrownBy(() -> SearchableValueParser.parseValue("999999999999999999999", Integer.class))
            .isInstanceOf(SearchableParseException.class);

        // Invalid date
        assertThatThrownBy(() -> SearchableValueParser.parseValue("2023-13-32", LocalDate.class))
            .isInstanceOf(SearchableParseException.class);

        // Invalid time
        assertThatThrownBy(() -> SearchableValueParser.parseValue("25:99:99", LocalTime.class))
            .isInstanceOf(SearchableParseException.class);
    }

    @Test
    @DisplayName("Byte and Short values should be parsed correctly")
    void testByteAndShortParsing() {
        // Byte
        assertThat(SearchableValueParser.parseValue("127", Byte.class)).isEqualTo((byte) 127);
        assertThat(SearchableValueParser.parseValue("-128", byte.class)).isEqualTo((byte) -128);

        // Short
        assertThat(SearchableValueParser.parseValue("32767", Short.class)).isEqualTo((short) 32767);
        assertThat(SearchableValueParser.parseValue("-32768", short.class)).isEqualTo((short) -32768);

        // Overflow
        assertThatThrownBy(() -> SearchableValueParser.parseValue("128", Byte.class))
            .isInstanceOf(SearchableParseException.class);
        assertThatThrownBy(() -> SearchableValueParser.parseValue("32768", Short.class))
            .isInstanceOf(SearchableParseException.class);
    }

    @Test
    @DisplayName("Timezone-aware types should preserve timezone information")
    void testTimezonePreservationForTimezoneAwareTypes() {
        // Test ZonedDateTime preserves timezone info
        String utcTime = "2024-12-31T15:00:00.000Z";
        ZonedDateTime zonedResult = (ZonedDateTime) SearchableValueParser.parseValue(utcTime, ZonedDateTime.class);
        
        // Should preserve UTC timezone
        assertThat(zonedResult.getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(zonedResult.toLocalDateTime()).isEqualTo(LocalDateTime.of(2024, 12, 31, 15, 0, 0));
        
        // Test OffsetDateTime preserves timezone info
        String offsetTime = "2024-12-31T20:00:00.000+05:00";
        OffsetDateTime offsetResult = (OffsetDateTime) SearchableValueParser.parseValue(offsetTime, OffsetDateTime.class);
        
        // Should preserve +05:00 offset
        assertThat(offsetResult.getOffset()).isEqualTo(ZoneOffset.ofHours(5));
        assertThat(offsetResult.toLocalDateTime()).isEqualTo(LocalDateTime.of(2024, 12, 31, 20, 0, 0));
        
        // Test Instant preserves the moment in time
        String instantTime = "2024-12-31T15:00:00.000Z";
        Instant instantResult = (Instant) SearchableValueParser.parseValue(instantTime, Instant.class);
        
        // Should represent the same moment in time
        assertThat(instantResult).isEqualTo(Instant.parse("2024-12-31T15:00:00.000Z"));
    }

    @Test
    @DisplayName("Timezone-aware types should use system timezone for non-timezone inputs")
    void testSystemTimezoneForNonTimezoneInputs() {
        // Test ZonedDateTime with no timezone info uses system default
        String noTimezone = "2024-12-31T15:00:00";
        ZonedDateTime zonedResult = (ZonedDateTime) SearchableValueParser.parseValue(noTimezone, ZonedDateTime.class);
        
        // Should use system default timezone (Asia/Seoul)
        assertThat(zonedResult.getZone()).isEqualTo(ZoneId.systemDefault());
        assertThat(zonedResult.toLocalDateTime()).isEqualTo(LocalDateTime.of(2024, 12, 31, 15, 0, 0));
        
        // Test OffsetDateTime with no timezone info uses system default
        OffsetDateTime offsetResult = (OffsetDateTime) SearchableValueParser.parseValue(noTimezone, OffsetDateTime.class);
        
        // Should use system default timezone offset
        assertThat(offsetResult.getOffset()).isEqualTo(ZoneId.systemDefault().getRules().getOffset(Instant.now()));
        assertThat(offsetResult.toLocalDateTime()).isEqualTo(LocalDateTime.of(2024, 12, 31, 15, 0, 0));
        
        // Test Instant with no timezone info uses system default
        Instant instantResult = (Instant) SearchableValueParser.parseValue(noTimezone, Instant.class);
        
        // Should convert using system default timezone
        Instant expected = LocalDateTime.of(2024, 12, 31, 15, 0, 0)
                .atZone(ZoneId.systemDefault())
                .toInstant();
        assertThat(instantResult).isEqualTo(expected);
    }

    @Test
    @DisplayName("LocalDateTime should convert timezone-aware inputs to server timezone")
    void testLocalDateTimeTimezoneConversion() {
        // Test UTC time conversion to Asia/Seoul (+09:00)
        String utcTime = "2024-12-31T15:00:00.000Z";
        LocalDateTime result = (LocalDateTime) SearchableValueParser.parseValue(utcTime, LocalDateTime.class);
        
        // UTC 15:00 should become Asia/Seoul 00:00 (next day) - converted to server timezone
        assertThat(result).isEqualTo(LocalDateTime.of(2025, 1, 1, 0, 0, 0));
        
        // Test positive offset
        String positiveOffset = "2024-12-31T20:00:00.000+05:00";
        LocalDateTime result2 = (LocalDateTime) SearchableValueParser.parseValue(positiveOffset, LocalDateTime.class);
        
        // +05:00 20:00 should become Asia/Seoul 00:00 (next day) - converted to server timezone
        assertThat(result2).isEqualTo(LocalDateTime.of(2025, 1, 1, 0, 0, 0));
        
        // Test negative offset
        String negativeOffset = "2024-12-31T01:00:00.000-05:00";
        LocalDateTime result3 = (LocalDateTime) SearchableValueParser.parseValue(negativeOffset, LocalDateTime.class);
        
        // -05:00 01:00 should become Asia/Seoul 15:00 (same day) - converted to server timezone
        assertThat(result3).isEqualTo(LocalDateTime.of(2024, 12, 31, 15, 0, 0));
        
        // Test no timezone info - should be used as-is
        String noTimezone = "2024-12-31T15:00:00";
        LocalDateTime result4 = (LocalDateTime) SearchableValueParser.parseValue(noTimezone, LocalDateTime.class);
        
        // No timezone info should be used as-is
        assertThat(result4).isEqualTo(LocalDateTime.of(2024, 12, 31, 15, 0, 0));
    }

    @Test
    @DisplayName("ZonedDateTime should parse date-only values correctly for between operations")
    void testZonedDateTimeParsingForBetween() {
        // Date-only input for start value (should be 00:00:00 with system timezone)
        ZonedDateTime expectedStart = LocalDateTime.of(2024, 12, 31, 0, 0, 0)
                .atZone(ZoneId.systemDefault());
        assertThat(SearchableValueParser.parseValueForBetween("2024-12-31", ZonedDateTime.class, false))
            .isEqualTo(expectedStart);

        // Date-only input for end value (should be 23:59:59.999999999 with system timezone)
        ZonedDateTime expectedEnd = LocalDateTime.of(2024, 12, 31, 23, 59, 59, 999_999_999)
                .atZone(ZoneId.systemDefault());
        assertThat(SearchableValueParser.parseValueForBetween("2024-12-31", ZonedDateTime.class, true))
            .isEqualTo(expectedEnd);

        // DateTime with timezone input should remain unchanged
        ZonedDateTime expectedDateTime = ZonedDateTime.parse("2024-12-31T15:30:45+09:00");
        assertThat(SearchableValueParser.parseValueForBetween("2024-12-31T15:30:45+09:00", ZonedDateTime.class, false))
            .isEqualTo(expectedDateTime);
        assertThat(SearchableValueParser.parseValueForBetween("2024-12-31T15:30:45+09:00", ZonedDateTime.class, true))
            .isEqualTo(expectedDateTime);
    }

    @Test
    @DisplayName("OffsetDateTime should parse date-only values correctly for between operations")
    void testOffsetDateTimeParsingForBetween() {
        // Date-only input for start value (should be 00:00:00 with system timezone offset)
        OffsetDateTime expectedStart = LocalDateTime.of(2024, 12, 31, 0, 0, 0)
                .atZone(ZoneId.systemDefault()).toOffsetDateTime();
        assertThat(SearchableValueParser.parseValueForBetween("2024-12-31", OffsetDateTime.class, false))
            .isEqualTo(expectedStart);

        // Date-only input for end value (should be 23:59:59.999999999 with system timezone offset)
        OffsetDateTime expectedEnd = LocalDateTime.of(2024, 12, 31, 23, 59, 59, 999_999_999)
                .atZone(ZoneId.systemDefault()).toOffsetDateTime();
        assertThat(SearchableValueParser.parseValueForBetween("2024-12-31", OffsetDateTime.class, true))
            .isEqualTo(expectedEnd);

        // DateTime with offset input should remain unchanged
        OffsetDateTime expectedDateTime = OffsetDateTime.parse("2024-12-31T15:30:45+09:00");
        assertThat(SearchableValueParser.parseValueForBetween("2024-12-31T15:30:45+09:00", OffsetDateTime.class, false))
            .isEqualTo(expectedDateTime);
        assertThat(SearchableValueParser.parseValueForBetween("2024-12-31T15:30:45+09:00", OffsetDateTime.class, true))
            .isEqualTo(expectedDateTime);
    }

    @Test
    @DisplayName("getSortFieldFromDto should return sortField when specified")
    void testGetSortFieldFromDto_WithSortField() {
        // Given
        String result = SearchableFieldUtils.getSortFieldFromDto(TestPostDTOs.TestPostSortFieldDTO.class, "authorName");
        
        // Then
        assertThat(result).isEqualTo("author.name");
    }

    @Test
    @DisplayName("getSortFieldFromDto should return createdAt when sortField is specified")
    void testGetSortFieldFromDto_WithSortFieldCreatedAt() {
        // Given
        String result = SearchableFieldUtils.getSortFieldFromDto(TestPostDTOs.TestPostSortFieldDTO.class, "createdDate");
        
        // Then
        assertThat(result).isEqualTo("createdAt");
    }

    @Test
    @DisplayName("getSortFieldFromDto should return sortField over entityField when both are specified")
    void testGetSortFieldFromDto_SortFieldOverEntityField() {
        // Given
        String result = SearchableFieldUtils.getSortFieldFromDto(TestPostDTOs.TestPostSortFieldDTO.class, "lastModified");
        
        // Then
        assertThat(result).isEqualTo("modifiedAt"); // sortField takes priority over entityField
    }

    @Test
    @DisplayName("getSortFieldFromDto should fallback to field name when no sortField or entityField")
    void testGetSortFieldFromDto_FallbackToFieldName() {
        // Given
        String result = SearchableFieldUtils.getSortFieldFromDto(TestPostDTOs.TestPostSortFieldDTO.class, "title");
        
        // Then
        assertThat(result).isEqualTo("title"); // No sortField or entityField, use field name
    }

    @Test
    @DisplayName("getSortFieldFromDto should return field name when class is null")
    void testGetSortFieldFromDto_NullClass() {
        // Given
        String result = SearchableFieldUtils.getSortFieldFromDto(null, "someField");
        
        // Then
        assertThat(result).isEqualTo("someField");
    }

    @Test
    @DisplayName("getSortFieldFromDto should return field name when field doesn't exist")
    void testGetSortFieldFromDto_NonExistentField() {
        // Given
        String result = SearchableFieldUtils.getSortFieldFromDto(TestPostDTOs.TestPostSortFieldDTO.class, "nonExistentField");
        
        // Then
        assertThat(result).isEqualTo("nonExistentField");
    }
} 