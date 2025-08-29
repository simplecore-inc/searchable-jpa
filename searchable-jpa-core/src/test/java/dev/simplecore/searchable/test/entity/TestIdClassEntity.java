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
@Table(name = "test_id_class_entity")
@IdClass(TestIdClassEntity.CompositeKey.class)
public class TestIdClassEntity {

    @Id
    @Column(name = "tenant_id")
    private String tenantId;
    
    @Id
    @Column(name = "entity_id")
    private Long entityId;

    @Column(nullable = false)
    private String name;

    @Column
    private String description;

    @Getter
    @Setter
    @EqualsAndHashCode
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompositeKey implements Serializable {
        
        private String tenantId;
        private Long entityId;
    }
} 