package dev.simplecore.searchable.core.utils;

import dev.simplecore.searchable.core.exception.SearchableParseException;
import dev.simplecore.searchable.core.i18n.MessageUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies issue 6-2 (UUID parsing) and 6-3/6-5 (localized parsing error messages).
 */
class SearchableValueParserUuidI18nTest {

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    private static boolean containsHangul(String text) {
        return text != null && text.codePoints().anyMatch(cp -> cp >= 0xAC00 && cp <= 0xD7A3);
    }

    // ---- 6-2: UUID conversion ----

    @Test
    @DisplayName("6-2: valid UUID string is converted to UUID")
    void validUuidIsParsed() {
        String raw = "550e8400-e29b-41d4-a716-446655440000";
        Object parsed = SearchableValueParser.parseValue(raw, UUID.class);
        assertThat(parsed).isInstanceOf(UUID.class).isEqualTo(UUID.fromString(raw));
    }

    @Test
    @DisplayName("6-2: invalid UUID string throws SearchableParseException")
    void invalidUuidThrows() {
        assertThatThrownBy(() -> SearchableValueParser.parseValue("not-a-uuid", UUID.class))
                .isInstanceOf(SearchableParseException.class);
    }

    // ---- 6-3: numeric/date parsing errors go through i18n ----

    @Test
    @DisplayName("6-3: numeric parse error is localized (Korean) instead of hardcoded English")
    void numericErrorIsLocalized() {
        LocaleContextHolder.setLocale(Locale.KOREAN);
        // parser.numeric.value.invalid must resolve to Korean text now that String.format was removed
        String message = MessageUtils.getMessage("parser.numeric.value.invalid",
                new Object[]{"abc", "Integer", "reason"});
        assertThat(containsHangul(message)).isTrue();

        assertThatThrownBy(() -> SearchableValueParser.parseValue("abc", Integer.class))
                .isInstanceOf(SearchableParseException.class)
                .satisfies(ex -> assertThat(containsHangul(ex.getMessage())).isTrue());
    }

    @Test
    @DisplayName("6-3: date parse error is localized (Korean)")
    void dateErrorIsLocalized() {
        LocaleContextHolder.setLocale(Locale.KOREAN);
        String message = MessageUtils.getMessage("parser.date.invalid", new Object[]{"garbage"});
        assertThat(containsHangul(message)).isTrue();
    }

    @Test
    @DisplayName("6-3: zoned/offset/instant/timezone parse messages are localized (Korean)")
    void temporalErrorsAreLocalized() {
        LocaleContextHolder.setLocale(Locale.KOREAN);
        assertThat(containsHangul(MessageUtils.getMessage("parser.zoneddatetime.invalid", new Object[]{"x"}))).isTrue();
        assertThat(containsHangul(MessageUtils.getMessage("parser.offsetdatetime.invalid", new Object[]{"x"}))).isTrue();
        assertThat(containsHangul(MessageUtils.getMessage("parser.instant.invalid", new Object[]{"x"}))).isTrue();
        assertThat(containsHangul(MessageUtils.getMessage("parser.datetime.timezone.failed", new Object[]{"x"}))).isTrue();
        assertThat(containsHangul(MessageUtils.getMessage("parser.uuid.invalid", new Object[]{"x"}))).isTrue();
        assertThat(containsHangul(MessageUtils.getMessage("parser.numeric.type.unsupported", new Object[]{"x"}))).isTrue();
    }

    // ---- 6-5: Korean bundle no longer contains embedded English ----

    @Test
    @DisplayName("6-5: character parse message is fully Korean under Korean locale")
    void characterMessageFullyKorean() {
        LocaleContextHolder.setLocale(Locale.KOREAN);
        String message = MessageUtils.getMessage("parser.character.invalid", new Object[]{"ab"});
        assertThat(containsHangul(message)).isTrue();
        // The previously-embedded English fragment must be gone.
        assertThat(message).doesNotContain("Value must be exactly one character long");
    }
}
