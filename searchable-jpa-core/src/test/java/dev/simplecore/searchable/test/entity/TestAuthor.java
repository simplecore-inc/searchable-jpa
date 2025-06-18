package dev.simplecore.searchable.test.entity;

import lombok.*;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "test_author")
@Getter
@Setter
@ToString(exclude = {"posts", "comments"})
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestAuthor extends AuditingBaseEntity<Long> {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long authorId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String nickname;

    @Column
    private String description;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "author", cascade = CascadeType.ALL)
    @Builder.Default
    private List<TestPost> posts = new ArrayList<>();

    @OneToMany(mappedBy = "author", cascade = CascadeType.ALL)
    @Builder.Default
    private List<TestComment> comments = new ArrayList<>();

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

    public void addPost(TestPost post) {
        posts.add(post);
        post.setAuthor(this);
    }

    public void removePost(TestPost post) {
        posts.remove(post);
        post.setAuthor(null);
    }

    public void addComment(TestComment comment) {
        comments.add(comment);
        comment.setAuthor(this);
    }

    public void removeComment(TestComment comment) {
        comments.remove(comment);
        comment.setAuthor(null);
    }

    //----------------------------------

    @Override
    public Long getId() {
        return this.authorId;
    }

    @Override
    public void setId(Long id) {
        this.authorId = id;
    }
}