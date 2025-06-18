package dev.simplecore.searchable.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "searchable")
public class SearchableProperties {
    private SwaggerProperties swagger = new SwaggerProperties();

    @Data
    public static class SwaggerProperties {
        private boolean enabled = true;
    }
} 