package dev.simplecore.searchable.core.service.specification;

import lombok.extern.slf4j.Slf4j;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.ManagedType;
import jakarta.persistence.metamodel.PluralAttribute;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

/**
 * Analyzes entity relationships to detect ToOne, ToMany, and nested relationships.
 * Used for optimizing JOIN strategies and preventing N+1 problems.
 */
@Slf4j
public class RelationshipAnalyzer<T> {

    private final EntityManager entityManager;
    private final Class<T> entityClass;

    public RelationshipAnalyzer(EntityManager entityManager, Class<T> entityClass) {
        this.entityManager = entityManager;
        this.entityClass = entityClass;
    }

    /**
     * Detect commonly accessed ToOne relationships to prevent N+1 problems.
     * This analyzes the entity class to find ManyToOne and OneToOne fields
     * that are likely to be accessed frequently, including nested relationships.
     */
    public Set<String> detectCommonToOneFields() {
        Set<String> commonFields = new HashSet<>();

        try {
            log.trace("DetectCommonToOneFields: Analyzing entity class: {}", entityClass.getSimpleName());

            // Use JPA metamodel to find ToOne relationships
            EntityType<T> entityType = entityManager.getMetamodel().entity(entityClass);

            // Add all ManyToOne and OneToOne relationships
            entityType.getSingularAttributes().forEach(attr -> {
                if (attr.getPersistentAttributeType() == Attribute.PersistentAttributeType.MANY_TO_ONE ||
                        attr.getPersistentAttributeType() == Attribute.PersistentAttributeType.ONE_TO_ONE) {
                    commonFields.add(attr.getName());
                    log.trace("DetectCommonToOneFields: Found {} relationship: {}",
                            attr.getPersistentAttributeType(), attr.getName());
                }
            });

            // ENHANCEMENT: Detect nested ToOne relationships in ManyToMany related entities
            try {
                Set<String> nestedToOneFields = detectNestedToOneRelationships();
                commonFields.addAll(nestedToOneFields);
            } catch (Exception e) {
                log.warn("Failed to detect nested ToOne relationships, continuing without them: {}", e.getMessage());
            }

            log.info("DetectCommonToOneFields: Detected {} ToOne relationships for automatic fetch joining: {}",
                    commonFields.size(), commonFields);

        } catch (Exception e) {
            log.warn("DetectCommonToOneFields: Metamodel analysis failed, falling back to reflection: {}", e.getMessage());

            // Fallback to reflection if metamodel fails
            Field[] fields = entityClass.getDeclaredFields();
            for (Field field : fields) {
                if (field.isAnnotationPresent(jakarta.persistence.ManyToOne.class) ||
                        field.isAnnotationPresent(jakarta.persistence.OneToOne.class)) {
                    commonFields.add(field.getName());
                    log.trace("DetectCommonToOneFields: Found ToOne field via reflection: {}", field.getName());
                }
            }

            log.info("DetectCommonToOneFields: Reflection fallback detected {} ToOne relationships: {}",
                    commonFields.size(), commonFields);
        }

        return commonFields;
    }

    /**
     * Detect nested ToOne relationships in ManyToMany and OneToMany related entities.
     * This prevents N+1 problems in scenarios like UserAccount.organizations.parent.
     */
    public Set<String> detectNestedToOneRelationships() {
        Set<String> nestedToOneFields = new HashSet<>();

        try {
            log.trace("DetectNestedToOneRelationships: Analyzing nested relationships for entity: {}", entityClass.getSimpleName());

            EntityType<T> entityType = entityManager.getMetamodel().entity(entityClass);

            // Analyze ManyToMany relationships
            entityType.getPluralAttributes().forEach(attr -> {
                if (attr.getPersistentAttributeType() == Attribute.PersistentAttributeType.MANY_TO_MANY) {
                    String relationshipName = attr.getName();
                    Class<?> targetEntityClass = attr.getElementType().getJavaType();

                    log.trace("DetectNestedToOneRelationships: Analyzing ManyToMany relationship '{}' with target entity: {}",
                            relationshipName, targetEntityClass.getSimpleName());

                    // Find ToOne relationships in the target entity
                    Set<String> targetToOneFields = detectToOneFieldsForEntity(targetEntityClass);

                    // Create nested paths (e.g., "organizations.parent")
                    for (String targetToOneField : targetToOneFields) {
                        String nestedPath = relationshipName + "." + targetToOneField;
                        nestedToOneFields.add(nestedPath);
                        log.trace("DetectNestedToOneRelationships: Found nested ToOne path: {}", nestedPath);
                    }
                }
            });

            // Analyze OneToMany relationships
            entityType.getPluralAttributes().forEach(attr -> {
                if (attr.getPersistentAttributeType() == Attribute.PersistentAttributeType.ONE_TO_MANY) {
                    String relationshipName = attr.getName();
                    Class<?> targetEntityClass = attr.getElementType().getJavaType();

                    log.trace("DetectNestedToOneRelationships: Analyzing OneToMany relationship '{}' with target entity: {}",
                            relationshipName, targetEntityClass.getSimpleName());

                    // Find ToOne relationships in the target entity
                    Set<String> targetToOneFields = detectToOneFieldsForEntity(targetEntityClass);

                    // Create nested paths (e.g., "comments.author")
                    for (String targetToOneField : targetToOneFields) {
                        String nestedPath = relationshipName + "." + targetToOneField;
                        nestedToOneFields.add(nestedPath);
                        log.trace("DetectNestedToOneRelationships: Found nested ToOne path: {}", nestedPath);
                    }
                }
            });

            if (!nestedToOneFields.isEmpty()) {
                log.info("DetectNestedToOneRelationships: Detected {} nested ToOne relationships: {}",
                        nestedToOneFields.size(), nestedToOneFields);
            }

        } catch (Exception e) {
            log.warn("DetectNestedToOneRelationships: Failed to analyze nested relationships: {}", e.getMessage());
        }

        return nestedToOneFields;
    }

    /**
     * Detect ToOne relationships for a specific entity class.
     * This is used to analyze target entities in ManyToMany/OneToMany relationships.
     */
    public Set<String> detectToOneFieldsForEntity(Class<?> entityClass) {
        Set<String> toOneFields = new HashSet<>();

        try {
            EntityType<?> entityType = entityManager.getMetamodel().entity(entityClass);

            entityType.getSingularAttributes().forEach(attr -> {
                if (attr.getPersistentAttributeType() == Attribute.PersistentAttributeType.MANY_TO_ONE ||
                        attr.getPersistentAttributeType() == Attribute.PersistentAttributeType.ONE_TO_ONE) {
                    toOneFields.add(attr.getName());
                    log.trace("DetectToOneFieldsForEntity: Found {} relationship '{}' in entity: {}",
                            attr.getPersistentAttributeType(), attr.getName(), entityClass.getSimpleName());
                }
            });

        } catch (Exception e) {
            log.trace("DetectToOneFieldsForEntity: Metamodel analysis failed for entity {}, falling back to reflection: {}",
                    entityClass.getSimpleName(), e.getMessage());

            // Fallback to reflection
            Field[] fields = entityClass.getDeclaredFields();
            for (Field field : fields) {
                if (field.isAnnotationPresent(jakarta.persistence.ManyToOne.class) ||
                        field.isAnnotationPresent(jakarta.persistence.OneToOne.class)) {
                    toOneFields.add(field.getName());
                    log.trace("DetectToOneFieldsForEntity: Found ToOne field '{}' via reflection in entity: {}",
                            field.getName(), entityClass.getSimpleName());
                }
            }
        }

        return toOneFields;
    }

    /**
     * Detect ManyToMany relationships that need batch loading to prevent N+1 problems.
     * This method analyzes the entity class to find ManyToMany fields.
     */
    public Set<String> detectManyToManyFields() {
        Set<String> manyToManyFields = new HashSet<>();

        try {
            log.trace("DetectManyToManyFields: Analyzing entity class: {}", entityClass.getSimpleName());

            // Use JPA metamodel to find ManyToMany relationships
            EntityType<T> entityType = entityManager.getMetamodel().entity(entityClass);

            // Find all ManyToMany relationships
            entityType.getPluralAttributes().forEach(attr -> {
                if (attr.getPersistentAttributeType() == Attribute.PersistentAttributeType.MANY_TO_MANY) {
                    manyToManyFields.add(attr.getName());
                    log.trace("DetectManyToManyFields: Found ManyToMany relationship: {}", attr.getName());
                }
            });

            if (!manyToManyFields.isEmpty()) {
                log.info("DetectManyToManyFields: Detected {} ManyToMany relationships: {}",
                        manyToManyFields.size(), manyToManyFields);
            }

        } catch (Exception e) {
            log.warn("DetectManyToManyFields: Metamodel analysis failed, falling back to reflection: {}", e.getMessage());

            // Fallback to reflection if metamodel fails
            Field[] fields = entityClass.getDeclaredFields();
            for (Field field : fields) {
                if (field.isAnnotationPresent(jakarta.persistence.ManyToMany.class)) {
                    manyToManyFields.add(field.getName());
                    log.trace("DetectManyToManyFields: Found ManyToMany field via reflection: {}", field.getName());
                }
            }

            log.info("DetectManyToManyFields: Reflection fallback detected {} ManyToMany relationships: {}",
                    manyToManyFields.size(), manyToManyFields);
        }

        return manyToManyFields;
    }

    /**
     * Checks if a path represents a ToMany relationship.
     */
    public boolean isToManyPath(Root<T> root, String path) {
        if (path == null || path.trim().isEmpty()) {
            return false;
        }

        try {
            String[] parts = path.split("\\.");
            Class<?> currentType = root.getJavaType();

            for (String part : parts) {
                ManagedType<?> managedType = entityManager.getMetamodel().managedType(currentType);
                Attribute<?, ?> attribute = managedType.getAttribute(part);

                if (attribute.isCollection()) {
                    return true;
                }

                // For nested paths, get the target entity type for next iteration
                if (attribute.getPersistentAttributeType() == Attribute.PersistentAttributeType.MANY_TO_ONE ||
                        attribute.getPersistentAttributeType() == Attribute.PersistentAttributeType.ONE_TO_ONE) {
                    currentType = attribute.getJavaType();
                } else if (attribute.getPersistentAttributeType() == Attribute.PersistentAttributeType.MANY_TO_MANY ||
                        attribute.getPersistentAttributeType() == Attribute.PersistentAttributeType.ONE_TO_MANY) {
                    // For collection attributes, get the element type
                    if (attribute instanceof PluralAttribute) {
                        currentType = ((PluralAttribute<?, ?, ?>) attribute).getElementType().getJavaType();
                    }
                } else {
                    currentType = attribute.getJavaType();
                }
            }

            return false;
        } catch (IllegalArgumentException e) {
            log.warn("Invalid path '{}' during ToMany analysis: {}", path, e.getMessage());
            return false; // Assume ToOne if path is invalid
        } catch (Exception e) {
            log.warn("Unexpected error analyzing path '{}': {}", path, e.getMessage());
            return false;
        }
    }

    /**
     * Count the number of ToMany relationships.
     */
    public int countToManyPaths(Set<String> paths, Root<T> root) {
        return (int) paths.stream()
                .filter(path -> isToManyPath(root, path))
                .count();
    }

    /**
     * Validates if a nested path is safe for JOIN operations.
     * Checks for valid entity relationships and prevents circular references.
     */
    public boolean isNestedPathSafeForJoin(Root<T> root, String nestedPath) {
        if (nestedPath == null || nestedPath.trim().isEmpty()) {
            return false;
        }

        try {
            String[] pathParts = nestedPath.split("\\.");
            if (pathParts.length > 3) {
                // Limit nesting depth to prevent performance issues
                log.trace("Nested path '{}' exceeds maximum depth (3), skipping for safety", nestedPath);
                return false;
            }

            Class<?> currentType = root.getJavaType();

            for (String part : pathParts) {
                EntityType<?> entityType = entityManager.getMetamodel().entity(currentType);
                Attribute<?, ?> attribute = entityType.getAttribute(part);

                // Only allow ToOne relationships in nested paths for safety
                if (attribute.getPersistentAttributeType() != Attribute.PersistentAttributeType.MANY_TO_ONE &&
                        attribute.getPersistentAttributeType() != Attribute.PersistentAttributeType.ONE_TO_ONE) {
                    log.trace("Nested path '{}' contains ToMany relationship at '{}', not safe for fetch join", nestedPath, part);
                    return false;
                }

                currentType = attribute.getJavaType();

                // Prevent circular references
                if (currentType.equals(root.getJavaType())) {
                    log.trace("Nested path '{}' contains circular reference, not safe for fetch join", nestedPath);
                    return false;
                }
            }

            log.trace("Nested path '{}' passed safety validation", nestedPath);
            return true;

        } catch (Exception e) {
            log.trace("Safety validation failed for nested path '{}': {}", nestedPath, e.getMessage());
            return false;
        }
    }

    /**
     * Validates if a path exists and is accessible in the entity metamodel.
     * This prevents "Unable to locate Attribute" errors during JOIN operations.
     */
    public boolean isValidPath(Root<T> root, String path) {
        if (path == null || path.trim().isEmpty()) {
            return false;
        }

        try {
            String[] pathParts = path.split("\\.");
            Class<?> currentType = root.getJavaType();

            for (String part : pathParts) {
                EntityType<?> entityType = entityManager.getMetamodel().entity(currentType);
                
                // Check if the attribute exists
                try {
                    Attribute<?, ?> attribute = entityType.getAttribute(part);
                    
                    // Get the next type for validation
                    if (attribute.getPersistentAttributeType() == Attribute.PersistentAttributeType.MANY_TO_ONE ||
                            attribute.getPersistentAttributeType() == Attribute.PersistentAttributeType.ONE_TO_ONE) {
                        currentType = attribute.getJavaType();
                    } else if (attribute.getPersistentAttributeType() == Attribute.PersistentAttributeType.MANY_TO_MANY ||
                            attribute.getPersistentAttributeType() == Attribute.PersistentAttributeType.ONE_TO_MANY) {
                        if (attribute instanceof PluralAttribute) {
                            currentType = ((PluralAttribute<?, ?, ?>) attribute).getElementType().getJavaType();
                        }
                    } else {
                        currentType = attribute.getJavaType();
                    }
                } catch (IllegalArgumentException e) {
                    log.trace("Path validation failed: attribute '{}' not found in entity '{}'", part, currentType.getSimpleName());
                    return false;
                }
            }

            return true;
        } catch (Exception e) {
            log.trace("Path validation failed for '{}': {}", path, e.getMessage());
            return false;
        }
    }
} 