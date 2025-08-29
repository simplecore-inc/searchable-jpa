package dev.simplecore.searchable.test.entity;


import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Comment;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.time.LocalDateTime;

@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditingBaseEntity<K> {

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    @Comment("Creator: User who initially created the record")
    private String createdBy;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    @Comment("Creation Date: Initial creation timestamp")
    private LocalDateTime createdAt;

    @LastModifiedBy
    @Column(name = "updated_by")
    @Comment("Modifier: User who last modified the record")
    private String updatedBy;

    @LastModifiedDate
    @Column(name = "updated_at")
    @Comment("Modification Date: Last modification timestamp")
    private LocalDateTime updatedAt;

    //----------------------------------
    public abstract K getId();

    public abstract void setId(K id);
}
