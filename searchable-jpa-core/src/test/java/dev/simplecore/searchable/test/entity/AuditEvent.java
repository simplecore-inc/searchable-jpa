package dev.simplecore.searchable.test.entity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
@Entity
public class AuditEvent {
    @Id @GeneratedValue public Long id;
    public Instant occurredAt;
    public enum Level { LOW, MID, HIGH }
    @Enumerated(EnumType.STRING) public Level level;
    public Long amount;
    @OneToMany(mappedBy="event",cascade=CascadeType.ALL) public List<AuditLabel> labels=new ArrayList<>();
}
