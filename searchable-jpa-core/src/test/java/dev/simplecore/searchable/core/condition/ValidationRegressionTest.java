package dev.simplecore.searchable.core.condition;

import dev.simplecore.searchable.core.annotation.SearchableField;
import dev.simplecore.searchable.core.condition.*;
import dev.simplecore.searchable.core.condition.operator.*;
import dev.simplecore.searchable.core.condition.parser.SearchableParamsParser;
import dev.simplecore.searchable.test.dto.InheritedSearchDTOs.ChildSearchDTO;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static dev.simplecore.searchable.core.condition.operator.SearchOperator.*;
import java.util.Map;

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
}
