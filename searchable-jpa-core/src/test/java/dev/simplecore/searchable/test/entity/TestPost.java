package dev.simplecore.searchable.test.entity;

import dev.simplecore.searchable.test.enums.TestPostStatus;
import lombok.*;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "test_post")
@Getter
@Setter
@ToString(exclude = {"author", "comments"})
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestPost extends AuditingBaseEntity<Long> {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long postId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column
    private TestPostStatus status;

    @Column(name = "view_count")
    private Long viewCount;

    @Column(name = "like_count")
    private Long likeCount;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private TestAuthor author;

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<TestComment> comments = new ArrayList<>();

    @ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE}, fetch = FetchType.LAZY)
    @JoinTable(
        name = "test_post_tag",
        joinColumns = @JoinColumn(name = "post_id"),
        inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    @Builder.Default
    private List<TestTag> tags = new ArrayList<>();

    @PrePersist
    void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.updatedAt == null) {
            this.updatedAt = this.createdAt;
        }
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void addComment(TestComment comment) {
        comments.add(comment);
        comment.setPost(this);
    }

    public void removeComment(TestComment comment) {
        comments.remove(comment);
        comment.setPost(null);
    }

    public void addTag(TestTag tag) {
        tags.add(tag);
        tag.getPosts().add(this);
    }

    public void removeTag(TestTag tag) {
        tags.remove(tag);
        tag.getPosts().remove(this);
    }

    //----------------------------------

    @Override
    public Long getId() {
        return this.postId;
    }

    @Override
    public void setId(Long id) {
        this.postId = id;
    }
}