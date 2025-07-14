package dev.simplecore.searchable.core.annotation;

import dev.simplecore.searchable.core.condition.operator.SearchOperator;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark fields as searchable and sortable in DTO classes.
 * This annotation is used to define how a field can be used in search conditions and sorting operations.
 *
 * <p>Example usage:
 * <pre>
 * public class UserDTO {
 *     {@literal @}SearchableField(entityField = "firstName", operators = {EQUALS, CONTAINS}, sortable = true)
 *     private String name;
 *
 *     {@literal @}SearchableField(operators = {GREATER_THAN, LESS_THAN})
 *     private Integer age;
 *
 *     {@literal @}SearchableField(sortable = true, sortField = "profile.displayName")
 *     private String displayName;
 * }
 * </pre>
 *
 * @see SearchOperator
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SearchableField {

    /**
     * Specifies the corresponding field name in the entity class.
     * This is useful when the DTO field name differs from the entity field name,
     * or when mapping to a nested entity field using dot notation.
     *
     * <p>Example:
     * <pre>
     * // Maps to user.address.city in the entity
     * {@literal @}SearchableField(entityField = "address.city")
     * private String city;
     * </pre>
     *
     * @return the entity field name, or empty string to use the annotated field name
     */
    String entityField() default "";

    /**
     * Defines which search operators are allowed for this field.
     * If empty, all operators are allowed.
     *
     * <p>Example:
     * <pre>
     * // Only allows EQUALS and CONTAINS operators
     * {@literal @}SearchableField(operators = {SearchOperator.EQUALS, SearchOperator.CONTAINS})
     * private String name;
     * </pre>
     *
     * @return array of allowed SearchOperators
     */
    SearchOperator[] operators() default {};

    /**
     * Indicates whether this field can be used for sorting operations.
     * When true, the field can be used in sort conditions.
     *
     * <p>Example:
     * <pre>
     * // Field can be used for both searching and sorting
     * {@literal @}SearchableField(sortable = true)
     * private LocalDateTime createdAt;
     * </pre>
     *
     * @return true if the field is sortable, false otherwise
     */
    boolean sortable() default false;

    /**
     * Specifies the field name to use for sorting operations.
     * This is useful when the field used for sorting differs from the field used for searching.
     * When specified, this field will be used for sorting instead of the entityField or the annotated field name.
     *
     * <p>Example:
     * <pre>
     * // Uses "profile.displayName" for sorting even though search uses "name"
     * {@literal @}SearchableField(entityField = "name", sortable = true, sortField = "profile.displayName")
     * private String displayName;
     * </pre>
     *
     * @return the field name to use for sorting, or empty string to use the default field
     */
    String sortField() default "";
} 