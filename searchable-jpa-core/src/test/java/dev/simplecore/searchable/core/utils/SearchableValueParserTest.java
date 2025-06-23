package dev.simplecore.searchable.core.utils;

import dev.simplecore.searchable.core.exception.SearchableParseException;
import dev.simplecore.searchable.test.config.BaseTestConfig;
import dev.simplecore.searchable.test.config.TestConfig;
import dev.simplecore.searchable.test.enums.TestPostStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.*;
import java.util.Date;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ContextConfiguration(classes = {BaseTestConfig.class, TestConfig.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@Transactional
class SearchableValueParserTest {

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
    @DisplayName("LocalDate should be parsed correctly")
    void testLocalDateParsing() {
        LocalDate expected = LocalDate.of(2023, 12, 25);

        // Various formats
        assertThat(SearchableValueParser.parseValue("2023-12-25", LocalDate.class)).isEqualTo(expected);
        assertThat(SearchableValueParser.parseValue("2023/12/25", LocalDate.class)).isEqualTo(expected);
        assertThat(SearchableValueParser.parseValue("25-12-2023", LocalDate.class)).isEqualTo(expected);
        assertThat(SearchableValueParser.parseValue("20231225", LocalDate.class)).isEqualTo(expected);

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
} 