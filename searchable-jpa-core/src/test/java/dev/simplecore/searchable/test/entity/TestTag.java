package dev.simplecore.searchable.test.entity;

import lombok.*;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "test_tag")
@Getter
@Setter
@ToString(exclude = {"posts"})
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestTag extends AuditingBaseEntity<Long> {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long tagId;

    @Column(nullable = false, unique = true)
    private String name;

    @Column
    private String description;

    @Column
    private String color;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @ManyToMany(mappedBy = "tags", fetch = FetchType.LAZY)
    @Builder.Default
    private List<TestPost> posts = new ArrayList<>();

    @PrePersist
    void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    //----------------------------------

    @Override
    public Long getId() {
        return this.tagId;
    }

    @Override
    public void setId(Long id) {
        this.tagId = id;
    }
} 