package dev.simplecore.searchable.core.utils;

import dev.simplecore.searchable.core.exception.SearchableParseException;

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
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")
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
                    String.format("Failed to parse value '%s' to type %s: %s",
                            value, targetType.getSimpleName(), e.getMessage()), e);
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
                                String.format("Invalid enum value '%s' for type %s. Allowed values are: %s",
                                        value, enumType.getSimpleName(),
                                        String.join(", ", getEnumNames(enumType))));
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
                        "Invalid boolean value: '" + value + "'. " +
                                "Allowed values are: true/false, 1/0, yes/no, y/n, on/off");
        }
    }

    private static Character parseCharacter(String value) {
        if (value.length() != 1) {
            throw new SearchableParseException(
                    "Invalid character value: '" + value + "'. " +
                            "Value must be exactly one character long");
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
                    String.format("Failed to parse temporal value '%s' to type %s. Error: %s",
                            value, targetType.getSimpleName(), e.getMessage()), e);
        }
        throw new SearchableParseException("Unsupported temporal type: " + targetType.getSimpleName());
    }

    private static LocalDateTime parseLocalDateTime(String value) {
        // First try direct parse
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException ignored) {
        }

        // Then try with formatters
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

        throw new SearchableParseException(String.format("Unsupported datetime format: '%s'", value), lastError);
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

        throw new SearchableParseException(String.format("Unsupported date format: '%s'", value), lastError);
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

        throw new SearchableParseException(String.format("Unsupported time format: '%s'", value), lastError);
    }

    private static ZonedDateTime parseZonedDateTime(String value) {
        try {
            // First try direct parse
            return ZonedDateTime.parse(value);
        } catch (DateTimeParseException e) {
            // Try parsing as LocalDateTime and use system default zone
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
            // Try parsing as LocalDateTime and use system default offset
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
            // Try parsing as LocalDateTime and convert to Instant
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
} 