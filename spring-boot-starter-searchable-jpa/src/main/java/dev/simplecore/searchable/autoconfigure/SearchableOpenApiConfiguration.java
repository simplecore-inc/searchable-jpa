package dev.simplecore.searchable.autoconfigure;

import dev.simplecore.searchable.openapi.customiser.OpenApiDocCustomiser;
import dev.simplecore.searchable.properties.SearchableProperties;

import io.swagger.v3.oas.models.OpenAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SearchableProperties.class)
@ConditionalOnProperty(name = "searchable.swagger.enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({OpenAPI.class, OperationCustomizer.class})
@AutoConfiguration
@AutoConfigureAfter(WebMvcAutoConfiguration.class)
public class SearchableOpenApiConfiguration {
    private static final Logger log = LoggerFactory.getLogger(SearchableOpenApiConfiguration.class);

    @Bean(name = "searchConditionCustomizer")
    @ConditionalOnMissingBean(name = "searchConditionCustomizer")
    @ConditionalOnBean(RequestMappingHandlerMapping.class)
    public OperationCustomizer searchConditionCustomizer(
            @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping
    ) {
        log.info("Creating SearchConditionDocCustomiser bean");
        log.debug("RequestMappingHandlerMapping available: {}", handlerMapping != null);
        return new OpenApiDocCustomiser(handlerMapping);
    }
}