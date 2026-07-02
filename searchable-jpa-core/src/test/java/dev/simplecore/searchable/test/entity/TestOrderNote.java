package dev.simplecore.searchable.test.entity;

import lombok.Getter;
import lombok.Setter;

import jakarta.persistence.*;

/**
 * Child of {@link TestOrderItem} via a composite foreign key. Used to exercise to-many joins against
 * a composite-key parent (counting and sorting).
 */
@Entity
@Getter
@Setter
@Table(name = "test_order_note")
public class TestOrderNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long noteId;

    @Column(nullable = false)
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
            @JoinColumn(name = "warehouse_code", referencedColumnName = "warehouse_code"),
            @JoinColumn(name = "line_no", referencedColumnName = "line_no")
    })
    private TestOrderItem orderItem;
}
