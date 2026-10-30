package dev.simplecore.searchable.core.condition;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class JacksonMvcCompatibilityTest {
    @RestController
    static class Controller {
        @PostMapping("/search")
        SearchCondition<ValidationRegressionTest.Dto> search(@RequestBody SearchCondition<ValidationRegressionTest.Dto> condition) {
            return condition;
        }
    }

    @Test void mvcUsesTheSameWireFormatAndValidationAsJackson2() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new Controller()).build();
        verify(mvc);
        verify(MockMvcBuilders.standaloneSetup(new Controller())
                .setMessageConverters(new MappingJackson2HttpMessageConverter()).build());
    }

    private void verify(MockMvc mvc) throws Exception {
        String json = "{\"conditions\":[{\"field\":\"label\",\"searchOperator\":\"equals\",\"value\":\"A\"}],\"sort\":{\"orders\":[{\"field\":\"label\",\"direction\":\"asc\"}]}}";
        String response = mvc.perform(post("/search").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var parsed = SearchCondition.fromJson(response, ValidationRegressionTest.Dto.class);
        assertThat(parsed.getNodes()).hasSize(1);
        assertThat(parsed.getSort().getOrders().get(0).getEntityField()).isEqualTo("name");
        mvc.perform(post("/search").contentType(MediaType.APPLICATION_JSON).content(json.replace("label", "unapproved")))
                .andExpect(status().isBadRequest());
    }
}
