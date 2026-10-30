package dev.simplecore.searchable.test.entity;
import jakarta.persistence.*;
@Entity public class AuditLabel {
    @Id @GeneratedValue public Long id;
    public String name;
    @ManyToOne public AuditEvent event;
}
