package dev.simplecore.searchable.core.condition;

import dev.simplecore.searchable.core.annotation.SearchableField;
import dev.simplecore.searchable.core.condition.*;
import dev.simplecore.searchable.core.condition.operator.*;
import dev.simplecore.searchable.core.condition.parser.SearchableParamsParser;
import dev.simplecore.searchable.test.dto.InheritedSearchDTOs.ChildSearchDTO;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static dev.simplecore.searchable.core.condition.operator.SearchOperator.*;
import static org.assertj.core.api.Assertions.*;

public class ValidationRegressionTest {
    public static class Dto {
        @SearchableField(operators={EQUALS}, sortable=true, entityField="status", sortField="name")
        private String label;
        public String getLabel(){return label;}
        public void setLabel(String v){label=v;}
    }
    public static class DefaultDto {
        @SearchableField private String name;
        public String getName(){return name;}
        public void setName(String v){name=v;}
    }
    public static class ConstraintDto {
        @SearchableField(operators={EQUALS}) private String name;
        @NotNull private String unrelated;
        public String getName(){return name;}
        public void setName(String v){name=v;}
        public String getUnrelated(){return unrelated;}
        public void setUnrelated(String v){unrelated=v;}
    }
    @Test void defaultOperatorsShouldAllowEquals() {
        SearchConditionBuilder.create(DefaultDto.class).where(w->w.equals("name","A")).build();
    }
    @Test void inheritedFieldShouldBeSearchable() {
        SearchConditionBuilder.create(ChildSearchDTO.class).where(w->w.equals("createdDate","2026-01-01")).build();
    }
    @Test void unrelatedRequiredFieldShouldNotRejectFilter() {
        SearchConditionBuilder.create(ConstraintDto.class).where(w->w.equals("name","A")).build();
    }
    @Test void jsonSortShouldResolveDtoMapping() throws Exception {
        var c=SearchCondition.fromJson("{\"sort\":{\"orders\":[{\"field\":\"label\",\"direction\":\"asc\"}]}}",Dto.class);
        assertThat(c.getSort().getOrders().get(0).getEntityField()).isEqualTo("name");
    }
    @Test void nestedSingleGroupMustRetainOrConnector() throws Exception {
        var c=SearchCondition.fromJson("""
        {"conditions":[{"conditions":[
          {"field":"label","searchOperator":"equals","value":"A"},
          {"operator":"or","conditions":[{"field":"label","searchOperator":"equals","value":"B"}]}
        ]}]}
        """,Dto.class);
        var outer=(SearchCondition.Group)c.getNodes().get(0);
        assertThat(outer.getNodes().get(1).getOperator()).isEqualTo(LogicalOperator.OR);
    }
    @Test void jackson3ShouldDeserializeNonemptySearch() {
        var mapper=JsonMapper.builder().build();
        var type=mapper.getTypeFactory().constructParametricType(SearchCondition.class,Dto.class);
        var c=(SearchCondition<?>)mapper.readValue("{\"conditions\":[{\"field\":\"label\",\"searchOperator\":\"equals\",\"value\":\"A\"}]}",type);
        assertThat(c.getNodes()).hasSize(1);
    }
    @Test void jackson3ShouldRejectUnapprovedSortField() {
        var mapper=JsonMapper.builder().build();
        var type=mapper.getTypeFactory().constructParametricType(SearchCondition.class,Dto.class);
        assertThatThrownBy(()->mapper.readValue("{\"sort\":{\"orders\":[{\"field\":\"secret\",\"direction\":\"ASC\"}]}}",type));
    }
    @Test void unknownGroupOperatorIsRejected() {
        assertThatThrownBy(() -> SearchCondition.fromJson(
                "{\"conditions\":[{\"operator\":\"xor\",\"conditions\":[]}]}", Dto.class))
                .hasMessageContaining("xor");
    }
}
