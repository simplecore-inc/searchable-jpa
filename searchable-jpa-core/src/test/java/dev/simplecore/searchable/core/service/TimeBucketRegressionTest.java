package dev.simplecore.searchable.core.service;

import dev.simplecore.searchable.core.condition.*;
import dev.simplecore.searchable.core.condition.operator.SearchOperator;
import dev.simplecore.searchable.core.service.bucket.TimeBucketCounter;
import dev.simplecore.searchable.test.config.BaseTestConfig;
import dev.simplecore.searchable.test.entity.AuditEvent;
import dev.simplecore.searchable.test.entity.AuditLabel;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:bucket-audit;DB_CLOSE_DELAY=-1", "spring.jpa.properties.hibernate.connection.provider_disables_autocommit=false", "spring.jpa.show-sql=false", "logging.level.org.hibernate.SQL=OFF"})
@ContextConfiguration(classes=BaseTestConfig.class)
@Transactional
public class TimeBucketRegressionTest {
    @Autowired EntityManager em;
    @Test void joinedCollectionShouldNotInflateBucketCount() {
        Instant start=Instant.parse("2026-01-01T00:00:00Z");
        AuditEvent event=new AuditEvent();event.occurredAt=start.plusMillis(10);for(String name:List.of("a","b")){var label=new AuditLabel();label.name=name;label.event=event;event.labels.add(label);}em.persist(event);em.flush();
        var condition=new SearchCondition<>();
        condition.getNodes().add(new SearchCondition.Condition("labels.name",SearchOperator.IN,List.of("a","b"),null));
        var repo=new SimpleJpaRepository<AuditEvent,Long>(AuditEvent.class,em);
        assertThat(new TimeBucketCounter(em).count(AuditEvent.class,repo,condition,"occurredAt",start,start.plusSeconds(1),1)).containsExactly(1L);
    }
    @Test void submillisecondWindowShouldNotDivideByZero() {
        Instant start=Instant.parse("2026-01-02T00:00:00Z");
        AuditEvent event=new AuditEvent();event.occurredAt=start.plusNanos(100_000);em.persist(event);em.flush();
        var repo=new SimpleJpaRepository<AuditEvent,Long>(AuditEvent.class,em);
        assertThat(new TimeBucketCounter(em).count(AuditEvent.class,repo,new SearchCondition<>(),"occurredAt",start,start.plusNanos(900_000),1)).containsExactly(1L);
    }
    @Test void unevenBucketEdgesAndEndBoundaryAreExact() {
        Instant start = Instant.parse("2026-02-01T00:00:00Z");
        for (long millis : List.of(0L, 3L, 4L, 6L, 7L, 9L, 10L)) {
            AuditEvent event = new AuditEvent(); event.occurredAt = start.plusMillis(millis); em.persist(event);
        }
        em.flush();
        var repo = new SimpleJpaRepository<AuditEvent, Long>(AuditEvent.class, em);
        assertThat(new TimeBucketCounter(em).count(AuditEvent.class, repo, new SearchCondition<>(),
                "occurredAt", start, start.plusMillis(10), 3)).containsExactly(2L, 2L, 2L);
    }
}
