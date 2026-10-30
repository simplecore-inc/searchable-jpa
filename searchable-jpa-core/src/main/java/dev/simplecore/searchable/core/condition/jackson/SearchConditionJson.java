package dev.simplecore.searchable.core.condition.jackson;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import tools.jackson.databind.annotation.JsonDeserialize;

/** Jackson 3 annotation bundle alongside the existing Jackson 2 annotation. */
@JacksonAnnotationsInside
@JsonDeserialize(using = SearchConditionJackson3Deserializer.class)
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SearchConditionJson { }
