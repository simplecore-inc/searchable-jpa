package dev.simplecore.searchable.core.condition;

import dev.simplecore.searchable.core.condition.SearchCondition.Condition;
import dev.simplecore.searchable.core.condition.parser.SearchableParamsParser;
import dev.simplecore.searchable.core.exception.SearchableParseException;
import dev.simplecore.searchable.core.exception.SearchableValidationException;
import dev.simplecore.searchable.test.dto.TestPostDTOs.TestPostSearchDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies Category 2 (JSON / parameter parsing) issues 2-1 through 2-8.
 */
class SearchableParsingIssuesTest {

    private static boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        for (Throwable t = throwable; t != null; t = t.getCause()) {
            if (type.isInstance(t)) {
                return true;
            }
        }
        return false;
    }

    private SearchCondition<TestPostSearchDTO> fromJson(String json) throws Exception {
        return SearchCondition.fromJson(json, TestPostSearchDTO.class);
    }

    // ---- 2-1: top-level IN/NOT_IN array is preserved ----

    @Test
    @DisplayName("2-1: a top-level IN condition keeps its array value")
    void topLevelInArrayPreserved() throws Exception {
        String json = "{\"conditions\":[{\"field\":\"status\",\"searchOperator\":\"in\",\"value\":[\"PUBLISHED\",\"DRAFT\"]}]}";

        SearchCondition<TestPostSearchDTO> condition = fromJson(json);
        Condition parsed = (Condition) condition.getNodes().get(0);

        assertThat(parsed.getValue()).isInstanceOf(List.class);
        List<?> values = (List<?>) parsed.getValue();
        assertThat(values).hasSize(2);
        assertThat(values.get(0)).isEqualTo("PUBLISHED");
        assertThat(values.get(1)).isEqualTo("DRAFT");
    }

    // ---- 2-2: missing field / searchOperator -> validation error, not NPE ----

    @Test
    @DisplayName("2-2: missing 'field' throws a validation exception, not a NullPointerException")
    void missingFieldThrowsValidation() {
        String json = "{\"conditions\":[{\"searchOperator\":\"equals\",\"value\":\"x\"}]}";

        assertThatThrownBy(() -> fromJson(json))
                .satisfies(ex -> assertThat(hasCause(ex, SearchableValidationException.class)).isTrue())
                .satisfies(ex -> assertThat(hasCause(ex, NullPointerException.class)).isFalse());
    }

    @Test
    @DisplayName("2-2: missing 'searchOperator' throws a validation exception, not a NullPointerException")
    void missingSearchOperatorThrowsValidation() {
        String json = "{\"conditions\":[{\"field\":\"searchTitle\",\"value\":\"x\"}]}";

        assertThatThrownBy(() -> fromJson(json))
                .satisfies(ex -> assertThat(hasCause(ex, SearchableValidationException.class)).isTrue())
                .satisfies(ex -> assertThat(hasCause(ex, NullPointerException.class)).isFalse());
    }

    // ---- 2-3: invalid logical operator -> validation error, not raw IllegalArgumentException ----

    @Test
    @DisplayName("2-3: an unknown logical operator throws a validation exception, not IllegalArgumentException")
    void invalidLogicalOperatorThrowsValidation() {
        String json = "{\"conditions\":[{\"operator\":\"xor\",\"field\":\"searchTitle\",\"searchOperator\":\"equals\",\"value\":\"ab\"}]}";

        assertThatThrownBy(() -> fromJson(json))
                .satisfies(ex -> assertThat(hasCause(ex, SearchableValidationException.class)).isTrue())
                .satisfies(ex -> assertThat(hasCause(ex, IllegalArgumentException.class)).isFalse());
    }

    // ---- 2-6: BETWEEN requires value2 ----

    @Test
    @DisplayName("2-6: BETWEEN without value2 fails validation")
    void betweenWithoutValue2Fails() {
        String json = "{\"conditions\":[{\"field\":\"viewCount\",\"searchOperator\":\"between\",\"value\":20}]}";

        assertThatThrownBy(() -> fromJson(json))
                .satisfies(ex -> assertThat(hasCause(ex, SearchableValidationException.class)).isTrue());
    }

    @Test
    @DisplayName("2-6: BETWEEN with both values is accepted")
    void betweenWithBothValuesAccepted() throws Exception {
        String json = "{\"conditions\":[{\"field\":\"viewCount\",\"searchOperator\":\"between\",\"value\":20,\"value2\":100}]}";

        SearchCondition<TestPostSearchDTO> condition = fromJson(json);
        assertThat(condition.getNodes()).hasSize(1);
    }

    // ---- 2-8: non-array 'conditions' is rejected clearly ----

    @Test
    @DisplayName("2-8: a non-array 'conditions' value is rejected at parse time")
    void nonArrayConditionsRejected() {
        String json = "{\"conditions\":{\"field\":\"searchTitle\"}}";

        assertThatThrownBy(() -> fromJson(json))
                .satisfies(ex -> assertThat(hasCause(ex, SearchableValidationException.class)).isTrue());
    }

    // ---- 2-4: comma splitting is limited to multi-value operators ----

    @Test
    @DisplayName("2-4: a single-value numeric operator keeps a grouped value intact")
    void commaNotSplitForSingleValueNumeric() {
        Map<String, String> params = new HashMap<>();
        params.put("viewCount.greaterThan", "1,000");

        SearchCondition<TestPostSearchDTO> condition = new SearchableParamsParser<>(TestPostSearchDTO.class).convert(params);
        Condition parsed = (Condition) condition.getNodes().get(0);

        assertThat(parsed.getValue()).isEqualTo(1000L);
    }

    @Test
    @DisplayName("2-4: a single-value pattern operator keeps a literal comma intact")
    void commaNotSplitForContains() {
        Map<String, String> params = new HashMap<>();
        params.put("searchTitle.contains", "Smith, Jr");

        SearchCondition<TestPostSearchDTO> condition = new SearchableParamsParser<>(TestPostSearchDTO.class).convert(params);
        Condition parsed = (Condition) condition.getNodes().get(0);

        assertThat(parsed.getValue()).isEqualTo("Smith, Jr");
    }

    @Test
    @DisplayName("2-4: IN still splits on comma into multiple values")
    void inStillSplitsOnComma() {
        Map<String, String> params = new HashMap<>();
        params.put("status.in", "PUBLISHED,DRAFT");

        SearchCondition<TestPostSearchDTO> condition = new SearchableParamsParser<>(TestPostSearchDTO.class).convert(params);
        Condition parsed = (Condition) condition.getNodes().get(0);

        assertThat(parsed.getValue()).isInstanceOf(List.class);
        List<?> values = (List<?>) parsed.getValue();
        assertThat(values).hasSize(2);
        assertThat(values.get(0)).isEqualTo("PUBLISHED");
        assertThat(values.get(1)).isEqualTo("DRAFT");
    }

    // ---- 2-5: invalid operator exposes the dedicated message ----

    @Test
    @DisplayName("2-5: an invalid operator surfaces the dedicated 'invalid operator' message")
    void invalidOperatorSurfacesDedicatedMessage() {
        Map<String, String> params = new HashMap<>();
        params.put("searchTitle.likez", "x");

        assertThatThrownBy(() -> new SearchableParamsParser<>(TestPostSearchDTO.class).convert(params))
                .isInstanceOf(SearchableParseException.class)
                .hasMessageContaining("likez");
    }

    // ---- 2-7: empty pattern value is rejected ----

    @Test
    @DisplayName("2-7: an empty CONTAINS value is rejected instead of matching everything")
    void emptyPatternValueRejected() {
        Map<String, String> params = new HashMap<>();
        params.put("searchTitle.contains", "");

        assertThatThrownBy(() -> new SearchableParamsParser<>(TestPostSearchDTO.class).convert(params))
                .isInstanceOf(SearchableValidationException.class);
    }
}
