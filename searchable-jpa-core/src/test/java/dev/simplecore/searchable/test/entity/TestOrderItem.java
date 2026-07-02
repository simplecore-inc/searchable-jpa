package dev.simplecore.searchable.test.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code @EmbeddedId} entity whose embedded id attribute is intentionally NOT named {@code "id"}
 * (it is {@code orderItemId}), and which owns a to-many collection of notes. Used to verify that the
 * two-phase executor resolves the embedded-id attribute name dynamically (issue 1-1) and counts /
 * sorts correctly through to-many relationships (issues 1-3, 1-4).
 */
@Entity
@Getter
@Setter
@Table(name = "test_order_item")
public class TestOrderItem {

    @EmbeddedId
    private OrderItemKey orderItemId;

    @Column(nullable = false)
    private String name;

    @OneToMany(mappedBy = "orderItem", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TestOrderNote> notes = new ArrayList<>();

    public void addNote(TestOrderNote note) {
        notes.add(note);
        note.setOrderItem(this);
    }

    @Embeddable
    @Getter
    @Setter
    @EqualsAndHashCode
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemKey implements Serializable {

        @Column(name = "warehouse_code")
        private String warehouseCode;

        @Column(name = "line_no")
        private Long lineNo;
    }
}
