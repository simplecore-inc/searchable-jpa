package dev.simplecore.searchable.test.entity;

import lombok.Getter;
import lombok.Setter;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import jakarta.persistence.*;
import java.io.Serializable;

@Entity
@Getter
@Setter
@Table(name = "test_composite_key_entity")
public class TestCompositeKeyEntity {

    @EmbeddedId
    private CompositeKey id;

    @Column(nullable = false)
    private String name;

    @Column
    private String description;

    @Embeddable
    @Getter
    @Setter
    @EqualsAndHashCode
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompositeKey implements Serializable {
        
        @Column(name = "tenant_id")
        private String tenantId;
        
        @Column(name = "entity_id")
        private Long entityId;
    }
} 