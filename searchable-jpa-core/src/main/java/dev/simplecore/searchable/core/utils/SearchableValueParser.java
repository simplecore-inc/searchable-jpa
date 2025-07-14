package dev.simplecore.searchable.core.utils;

import dev.simplecore.searchable.core.exception.SearchableParseException;
import dev.simplecore.searchable.core.i18n.MessageUtils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility class for parsing values to specific types.
 * Supports various data types including primitives, temporal types, and enums.
 */
public class SearchableValueParser {
    private static final Map<String, DateTimeFormatter> DATE_TIME_FORMATTER_CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Map<String, Object>> ENUM_CACHE = new ConcurrentHashMap<>();

    private static final DateTimeFormatter[] DATE_TIME_FORMATTERS = {
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSS")
    };

    // Additional formatters for timezone-aware datetime parsing
    private static final DateTimeFormatter[] TIMEZONE_AWARE_FORMATTERS = {
            DateTimeFormatter.ISO_OFFSET_DATE_TIME,
            DateTimeFormatter.ISO_ZONED_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSSXXX"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XXX"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z"),
            DateTimeFormatter.RFC_1123_DATE_TIME,
            // Additional RFC 3339 compliant formats
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SX"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSX"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SZ"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSZ"),
            // Common API formats with literal 'Z'
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"),
            // Alternative separators with timezone
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssXXX"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSXXX"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssZ"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSZ"),
            // Unix timestamp formats (seconds and milliseconds)
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSS")
    };

    private static final DateTimeFormatter[] DATE_FORMATTERS = {
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("yyyyMMdd"),
            DateTimeFormatter.ofPattern("yyyy.MM.dd"),
            DateTimeFormatter.BASIC_ISO_DATE
    };

    private static final DateTimeFormatter[] TIME_FORMATTERS = {
            DateTimeFormatter.ISO_LOCAL_TIME,
            DateTimeFormatter.ofPattern("HH:mm:ss"),
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS"),
            DateTimeFormatter.ofPattern("HH:mm"),
            DateTimeFormatter.ofPattern("HHmmss"),
            DateTimeFormatter.ofPattern("HHmm"),
            DateTimeFormatter.ofPattern("H:mm")
    };

    /**
     * Parse a string value to the specified target type for between operations.
     * For date types, if only date is provided (no time), it will be adjusted for range queries:
     * - Start value: set to beginning of day (00:00:00)
     * - End value: set to end of day (23:59:59.999999999)
     *
     * @param value      the string value to parse
     * @param targetType the target type to parse to
     * @param isEndValue true if this is the end value of a between operation
     * @return the parsed value
     * @throws SearchableParseException if parsing fails
     */
    public static Object parseValueForBetween(String value, Class<?> targetType, boolean isEndValue) {
        if (value == null || value.trim().isEmpty() || "null".equalsIgnoreCase(value.trim())) {
            return null;
        }

        // Normalize input value
        value = normalizeValue(value);

        try {
            // Handle temporal types with special between logic
            if (java.time.temporal.Temporal.class.isAssignableFrom(targetType) ||
                    java.util.Date.class.isAssignableFrom(targetType)) {
                return parseTemporalValueForBetween(value, targetType, isEndValue);
            }

            // For non-temporal types, use regular parsing
            return parseValue(value, targetType);
        } catch (Exception e) {
            throw new SearchableParseException(
                    MessageUtils.getMessage("parser.value.parse.failed", 
                            new Object[]{value, targetType.getSimpleName(), e.getMessage()}), e);
        }
    }

    /**
     * Parse a string value to the specified target type.
     *
     * @param value      the string value to parse
     * @param targetType the target type to parse to
     * @return the parsed value
     * @throws SearchableParseException if parsing fails
     */
    public static Object parseValue(String value, Class<?> targetType) {
        if (value == null || value.trim().isEmpty() || "null".equalsIgnoreCase(value.trim())) {
            return null;
        }

        // Normalize input value
        value = normalizeValue(value);

        try {
            // Handle enums
            if (targetType.isEnum()) {
                return parseEnum(value, targetType);
            }

            // Handle booleans
            if (targetType == Boolean.class || targetType == boolean.class) {
                return parseBoolean(value);
            }

            // Handle temporal types
            if (java.time.temporal.Temporal.class.isAssignableFrom(targetType) ||
                    java.util.Date.class.isAssignableFrom(targetType)) {
                return parseTemporalValue(value, targetType);
            }

            // Handle numbers
            if (Number.class.isAssignableFrom(targetType) || targetType.isPrimitive() && targetType != char.class) {
                return parseNumericValue(value, targetType);
            }

            // Handle character
            if (targetType == Character.class || targetType == char.class) {
                return parseCharacter(value);
            }

            // Return as string for String type or if no other type matches
            return value;
        } catch (Exception e) {
            throw new SearchableParseException(
                    MessageUtils.getMessage("parser.value.parse.failed", 
                            new Object[]{value, targetType.getSimpleName(), e.getMessage()}), e);
        }
    }

    private static String normalizeValue(String value) {
        value = value.trim();
        // Remove BOM if present
        if (value.startsWith("\uFEFF")) {
            value = value.substring(1);
        }
        // Normalize Unicode
        return java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFKC);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object parseEnum(String value, Class<?> enumType) {
        String normalizedValue = value.toUpperCase();
        return ENUM_CACHE
                .computeIfAbsent(enumType, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(normalizedValue, k -> {
                    try {
                        return Enum.valueOf((Class<Enum>) enumType, normalizedValue);
                    } catch (IllegalArgumentException e) {
                        // Try case-insensitive match
                        for (Object enumConstant : enumType.getEnumConstants()) {
                            if (enumConstant.toString().equalsIgnoreCase(value)) {
                                return enumConstant;
                            }
                        }
                        throw new SearchableParseException(
                                MessageUtils.getMessage("parser.enum.invalid", 
                                        new Object[]{value, enumType.getSimpleName(),
                                        String.join(", ", getEnumNames(enumType))}));
                    }
                });
    }

    private static String[] getEnumNames(Class<?> enumType) {
        return Arrays.stream(enumType.getEnumConstants())
                .map(Object::toString)
                .toArray(String[]::new);
    }

    private static Boolean parseBoolean(String value) {
        value = value.toLowerCase();
        switch (value) {
            case "true":
            case "1":
            case "yes":
            case "y":
            case "on":
                return true;
            case "false":
            case "0":
            case "no":
            case "n":
            case "off":
                return false;
            default:
                throw new SearchableParseException(
                        MessageUtils.getMessage("parser.boolean.invalid", new Object[]{value}));
        }
    }

    private static Character parseCharacter(String value) {
        if (value.length() != 1) {
            throw new SearchableParseException(
                    MessageUtils.getMessage("parser.character.invalid", new Object[]{value}));
        }
        return value.charAt(0);
    }

    private static Object parseTemporalValue(String value, Class<?> targetType) {
        try {
            if (targetType == LocalDateTime.class) {
                return parseLocalDateTime(value);
            }
            if (targetType == LocalDate.class) {
                return parseLocalDate(value);
            }
            if (targetType == LocalTime.class) {
                return parseLocalTime(value);
            }
            if (targetType == ZonedDateTime.class) {
                return parseZonedDateTime(value);
            }
            if (targetType == OffsetDateTime.class) {
                return parseOffsetDateTime(value);
            }
            if (targetType == Instant.class) {
                return parseInstant(value);
            }
            if (targetType == Date.class) {
                return parseDate(value);
            }
            if (targetType == Year.class) {
                return Year.parse(value);
            }
            if (targetType == YearMonth.class) {
                return YearMonth.parse(value);
            }
            if (targetType == MonthDay.class) {
                return MonthDay.parse(value);
            }
        } catch (Exception e) {
            throw new SearchableParseException(
                    MessageUtils.getMessage("parser.temporal.parse.failed", 
                            new Object[]{value, targetType.getSimpleName(), e.getMessage()}), e);
        }
        throw new SearchableParseException(MessageUtils.getMessage("parser.temporal.type.unsupported", new Object[]{targetType.getSimpleName()}));
    }

    private static Object parseTemporalValueForBetween(String value, Class<?> targetType, boolean isEndValue) {
        try {
            if (targetType == LocalDateTime.class) {
                return parseLocalDateTimeForBetween(value, isEndValue);
            }
            if (targetType == LocalDate.class) {
                return parseLocalDate(value); // LocalDate doesn't need adjustment
            }
            if (targetType == LocalTime.class) {
                return parseLocalTime(value); // LocalTime doesn't need adjustment
            }
            if (targetType == ZonedDateTime.class) {
                return parseZonedDateTimeForBetween(value, isEndValue);
            }
            if (targetType == OffsetDateTime.class) {
                return parseOffsetDateTimeForBetween(value, isEndValue);
            }
            if (targetType == Instant.class) {
                return parseInstantForBetween(value, isEndValue);
            }
            if (targetType == Date.class) {
                return parseDateForBetween(value, isEndValue);
            }
            if (targetType == Year.class) {
                return Year.parse(value);
            }
            if (targetType == YearMonth.class) {
                return YearMonth.parse(value);
            }
            if (targetType == MonthDay.class) {
                return MonthDay.parse(value);
            }
        } catch (Exception e) {
            throw new SearchableParseException(
                    MessageUtils.getMessage("parser.temporal.parse.failed", 
                            new Object[]{value, targetType.getSimpleName(), e.getMessage()}), e);
        }
        throw new SearchableParseException(MessageUtils.getMessage("parser.temporal.type.unsupported", new Object[]{targetType.getSimpleName()}));
    }

    private static LocalDateTime parseLocalDateTime(String value) {
        // First try direct parse
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException ignored) {
        }

        // Try parsing timezone-aware formats and convert to system timezone
        // For LocalDateTime, if client provides timezone info, convert to server timezone and extract local part
        if (hasTimezoneInfo(value)) {
            try {
                return parseTimezoneAwareDateTime(value);
            } catch (DateTimeParseException ignored) {
            }
        }

        // Then try with standard formatters
        DateTimeParseException lastError = null;
        for (DateTimeFormatter formatter : DATE_TIME_FORMATTERS) {
            try {
                return LocalDateTime.parse(value, getCachedFormatter(formatter));
            } catch (DateTimeParseException e) {
                lastError = e;
            }
        }

        // Try parsing as LocalDate and append midnight time
        try {
            return parseLocalDate(value).atStartOfDay();
        } catch (Exception ignored) {
        }

        throw new SearchableParseException(MessageUtils.getMessage("parser.datetime.format.unsupported", new Object[]{value}), lastError);
    }

    private static boolean hasTimezoneInfo(String value) {
        return value.endsWith("Z") || 
               // More specific timezone patterns - must have time part before timezone
               value.matches(".*T\\d{2}:\\d{2}(:\\d{2})?(\\.\\d+)?[+-]\\d{2}:?\\d{2}$") ||
               value.matches(".*\\s\\d{2}:\\d{2}(:\\d{2})?(\\.\\d+)?[+-]\\d{2}:?\\d{2}$") ||
               // Only match +/-HHMM after time component, not after date
               value.matches(".*T\\d{2}:\\d{2}(:\\d{2})?(\\.\\d+)?[+-]\\d{4}$") ||
               value.contains("GMT") ||
               value.contains("UTC") ||
               value.matches(".*\\s[A-Z]{3,4}$"); // Common timezone abbreviations
    }

    private static LocalDateTime parseTimezoneAwareDateTime(String value) {
        // Try parsing as Instant first (for Z suffix)
        if (value.endsWith("Z")) {
            try {
                return Instant.parse(value).atZone(ZoneId.systemDefault()).toLocalDateTime();
            } catch (DateTimeParseException ignored) {
            }
        }

        // Try parsing with timezone-aware formatters
        DateTimeParseException lastError = null;
        for (DateTimeFormatter formatter : TIMEZONE_AWARE_FORMATTERS) {
            try {
                // Try parsing as OffsetDateTime first
                try {
                    OffsetDateTime offsetDateTime = OffsetDateTime.parse(value, getCachedFormatter(formatter));
                    return offsetDateTime.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
                } catch (DateTimeParseException e) {
                    // Try parsing as ZonedDateTime
                    ZonedDateTime zonedDateTime = ZonedDateTime.parse(value, getCachedFormatter(formatter));
                    return zonedDateTime.withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
                }
            } catch (DateTimeParseException e) {
                lastError = e;
            }
        }

        // Fallback: try parsing as OffsetDateTime with common patterns
        try {
            OffsetDateTime offsetDateTime = OffsetDateTime.parse(value);
            return offsetDateTime.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
        }

        throw new SearchableParseException(
                String.format("Failed to parse timezone-aware datetime: '%s'", value), lastError);
    }

    private static LocalDate parseLocalDate(String value) {
        // First try direct parse
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException ignored) {
        }

        // Then try with formatters
        DateTimeParseException lastError = null;
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(value, getCachedFormatter(formatter));
            } catch (DateTimeParseException e) {
                lastError = e;
            }
        }

        throw new SearchableParseException(MessageUtils.getMessage("parser.date.format.unsupported", new Object[]{value}), lastError);
    }

    private static LocalTime parseLocalTime(String value) {
        // First try direct parse
        try {
            return LocalTime.parse(value);
        } catch (DateTimeParseException ignored) {
        }

        // Then try with formatters
        DateTimeParseException lastError = null;
        for (DateTimeFormatter formatter : TIME_FORMATTERS) {
            try {
                return LocalTime.parse(value, getCachedFormatter(formatter));
            } catch (DateTimeParseException e) {
                lastError = e;
            }
        }

        throw new SearchableParseException(MessageUtils.getMessage("parser.time.format.unsupported", new Object[]{value}), lastError);
    }

    private static ZonedDateTime parseZonedDateTime(String value) {
        try {
            // First try direct parse
            return ZonedDateTime.parse(value);
        } catch (DateTimeParseException e) {
            // If client provides timezone info, parse and preserve it
            if (hasTimezoneInfo(value)) {
                try {
                    // Try parsing as OffsetDateTime first, then convert to ZonedDateTime
                    OffsetDateTime offsetDateTime = OffsetDateTime.parse(value);
                    return offsetDateTime.toZonedDateTime();
                } catch (DateTimeParseException ex) {
                    // Try parsing with timezone-aware formatters
                    for (DateTimeFormatter formatter : TIMEZONE_AWARE_FORMATTERS) {
                        try {
                            return ZonedDateTime.parse(value, getCachedFormatter(formatter));
                        } catch (DateTimeParseException ignored) {
                        }
                    }
                }
            }
            
            // If no timezone info, parse as LocalDateTime and use system default zone
            try {
                return parseLocalDateTime(value).atZone(ZoneId.systemDefault());
            } catch (Exception ignored) {
                throw new SearchableParseException(
                        String.format("Invalid zoned datetime format: '%s'. Expected ISO-8601 format with timezone", value));
            }
        }
    }

    private static OffsetDateTime parseOffsetDateTime(String value) {
        try {
            // First try direct parse
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException e) {
            // If client provides timezone info, parse and preserve it
            if (hasTimezoneInfo(value)) {
                try {
                    // Try parsing with timezone-aware formatters
                    for (DateTimeFormatter formatter : TIMEZONE_AWARE_FORMATTERS) {
                        try {
                            return OffsetDateTime.parse(value, getCachedFormatter(formatter));
                        } catch (DateTimeParseException ignored) {
                        }
                    }
                    
                    // Try parsing as ZonedDateTime first, then convert to OffsetDateTime
                    ZonedDateTime zonedDateTime = ZonedDateTime.parse(value);
                    return zonedDateTime.toOffsetDateTime();
                } catch (DateTimeParseException ignored) {
                }
            }
            
            // If no timezone info, parse as LocalDateTime and use system default offset
            try {
                return parseLocalDateTime(value)
                        .atZone(ZoneId.systemDefault())
                        .toOffsetDateTime();
            } catch (Exception ignored) {
                throw new SearchableParseException(
                        String.format("Invalid offset datetime format: '%s'. Expected ISO-8601 format with offset", value));
            }
        }
    }

    private static Instant parseInstant(String value) {
        try {
            // First try direct parse
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            // If client provides timezone info, parse and convert to Instant (preserving the moment in time)
            if (hasTimezoneInfo(value)) {
                try {
                    // Try parsing as OffsetDateTime first
                    OffsetDateTime offsetDateTime = OffsetDateTime.parse(value);
                    return offsetDateTime.toInstant();
                } catch (DateTimeParseException ex) {
                    try {
                        // Try parsing as ZonedDateTime
                        ZonedDateTime zonedDateTime = ZonedDateTime.parse(value);
                        return zonedDateTime.toInstant();
                    } catch (DateTimeParseException ex2) {
                        // Try parsing with timezone-aware formatters
                        for (DateTimeFormatter formatter : TIMEZONE_AWARE_FORMATTERS) {
                            try {
                                OffsetDateTime parsed = OffsetDateTime.parse(value, getCachedFormatter(formatter));
                                return parsed.toInstant();
                            } catch (DateTimeParseException ignored) {
                            }
                        }
                    }
                }
            }
            
            // If no timezone info, parse as LocalDateTime and convert to Instant using system timezone
            try {
                return parseLocalDateTime(value)
                        .atZone(ZoneId.systemDefault())
                        .toInstant();
            } catch (Exception ignored) {
                throw new SearchableParseException(
                        String.format("Invalid instant format: '%s'. Expected ISO-8601 format", value));
            }
        }
    }

    private static Date parseDate(String value) {
        try {
            return Date.from(parseInstant(value));
        } catch (Exception e) {
            try {
                return Date.from(parseLocalDateTime(value)
                        .atZone(ZoneId.systemDefault())
                        .toInstant());
            } catch (Exception ignored) {
                throw new SearchableParseException(
                        String.format("Invalid date format: '%s'", value));
            }
        }
    }

    private static Object parseNumericValue(String value, Class<?> targetType) {
        try {
            // Remove grouping separators and normalize decimal separator
            value = value.replace(",", "").replace(" ", "");

            if (targetType == Byte.class || targetType == byte.class) {
                return Byte.parseByte(value);
            }
            if (targetType == Short.class || targetType == short.class) {
                return Short.parseShort(value);
            }
            if (targetType == Integer.class || targetType == int.class) {
                return Integer.parseInt(value);
            }
            if (targetType == Long.class || targetType == long.class) {
                return Long.parseLong(value);
            }
            if (targetType == Float.class || targetType == float.class) {
                return Float.parseFloat(value);
            }
            if (targetType == Double.class || targetType == double.class) {
                return Double.parseDouble(value);
            }
            if (targetType == BigDecimal.class) {
                return new BigDecimal(value);
            }
            if (targetType == BigInteger.class) {
                return new BigInteger(value);
            }
        } catch (NumberFormatException e) {
            throw new SearchableParseException(
                    String.format("Invalid numeric value '%s' for type %s: %s",
                            value, targetType.getSimpleName(), e.getMessage()));
        }
        throw new SearchableParseException("Unsupported numeric type: " + targetType.getSimpleName());
    }

    private static DateTimeFormatter getCachedFormatter(DateTimeFormatter formatter) {
        return DATE_TIME_FORMATTER_CACHE.computeIfAbsent(
                formatter.toString(),
                k -> formatter
        );
    }

    /**
     * Parse LocalDateTime for between operations.
     * If only date is provided (no time), adjust for range queries.
     */
    private static LocalDateTime parseLocalDateTimeForBetween(String value, boolean isEndValue) {
        // Check if the original value was date-only first
        if (isDateOnly(value)) {
            // Parse as LocalDate first, then convert to LocalDateTime
            LocalDate date = parseLocalDate(value);
            if (isEndValue) {
                // For end value, set to end of day (23:59:59.999999999)
                return date.atTime(LocalTime.MAX);
            } else {
                // For start value, set to beginning of day (00:00:00.000000000)
                return date.atTime(LocalTime.MIN);
            }
        }
        
        // If not date-only, parse as regular LocalDateTime
        return parseLocalDateTime(value);
    }

    /**
     * Parse ZonedDateTime for between operations.
     * Preserves timezone information if provided by client.
     * For date-only values, uses system timezone.
     */
    private static ZonedDateTime parseZonedDateTimeForBetween(String value, boolean isEndValue) {
        if (isDateOnly(value)) {
            LocalDate date = parseLocalDate(value);
            LocalDateTime localDateTime;
            if (isEndValue) {
                localDateTime = date.atTime(LocalTime.MAX);
            } else {
                localDateTime = date.atTime(LocalTime.MIN);
            }
            return localDateTime.atZone(ZoneId.systemDefault());
        }
        
        return parseZonedDateTime(value);
    }

    /**
     * Parse OffsetDateTime for between operations.
     * Preserves timezone information if provided by client.
     * For date-only values, uses system timezone.
     */
    private static OffsetDateTime parseOffsetDateTimeForBetween(String value, boolean isEndValue) {
        if (isDateOnly(value)) {
            LocalDate date = parseLocalDate(value);
            LocalDateTime localDateTime;
            if (isEndValue) {
                localDateTime = date.atTime(LocalTime.MAX);
            } else {
                localDateTime = date.atTime(LocalTime.MIN);
            }
            return localDateTime.atZone(ZoneId.systemDefault()).toOffsetDateTime();
        }
        
        return parseOffsetDateTime(value);
    }

    /**
     * Parse Instant for between operations.
     * Preserves the moment in time if timezone information is provided by client.
     * For date-only values, uses system timezone.
     */
    private static Instant parseInstantForBetween(String value, boolean isEndValue) {
        if (isDateOnly(value)) {
            LocalDate date = parseLocalDate(value);
            LocalDateTime localDateTime;
            if (isEndValue) {
                localDateTime = date.atTime(LocalTime.MAX);
            } else {
                localDateTime = date.atTime(LocalTime.MIN);
            }
            return localDateTime.atZone(ZoneId.systemDefault()).toInstant();
        }
        
        return parseInstant(value);
    }

    /**
     * Parse Date for between operations.
     * Preserves the moment in time if timezone information is provided by client.
     * For date-only values, uses system timezone.
     */
    private static Date parseDateForBetween(String value, boolean isEndValue) {
        if (isDateOnly(value)) {
            LocalDate date = parseLocalDate(value);
            LocalDateTime localDateTime;
            if (isEndValue) {
                localDateTime = date.atTime(LocalTime.MAX);
            } else {
                localDateTime = date.atTime(LocalTime.MIN);
            }
            return Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
        }
        
        return parseDate(value);
    }

    /**
     * Check if the input string represents a date-only value (no time component).
     */
    private static boolean isDateOnly(String value) {
        // Remove timezone information for checking
        String cleanValue = value.replaceAll("[+-]\\d{2}:?\\d{2}$", "").replace("Z", "");
        
        // Check if it matches date-only patterns
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                LocalDate.parse(cleanValue, formatter);
                return true;
            } catch (DateTimeParseException ignored) {
            }
        }
        
        // Check if it's a simple date format without time
        return cleanValue.matches("^\\d{4}[-/.]\\d{1,2}[-/.]\\d{1,2}$") ||
               cleanValue.matches("^\\d{1,2}[-/.]\\d{1,2}[-/.]\\d{4}$") ||
               cleanValue.matches("^\\d{8}$");
    }
} 