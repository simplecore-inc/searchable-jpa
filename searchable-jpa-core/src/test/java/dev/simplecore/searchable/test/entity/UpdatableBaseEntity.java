package dev.simplecore.searchable.test.entity;

import lombok.Getter;
import lombok.Setter;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

/**
 * {@code @MappedSuperclass} exposing a non-auditing inherited column, used to verify that partial
 * updates copy fields declared in a mapped superclass (issue 5-2).
 */
@Getter
@Setter
@MappedSuperclass
public abstract class UpdatableBaseEntity {

    @Column(name = "category")
    private String category;
}
