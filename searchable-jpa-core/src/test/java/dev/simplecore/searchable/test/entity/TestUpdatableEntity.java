package dev.simplecore.searchable.test.entity;

import lombok.Getter;
import lombok.Setter;

import jakarta.persistence.*;

/**
 * Simple single primary-key entity extending {@link UpdatableBaseEntity}, used to verify
 * {@code updateWithSearch} behavior (issues 5-2, 5-5).
 */
@Entity
@Getter
@Setter
@Table(name = "test_updatable")
public class TestUpdatableEntity extends UpdatableBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column
    private String name;

    @Column
    private String status;
}
