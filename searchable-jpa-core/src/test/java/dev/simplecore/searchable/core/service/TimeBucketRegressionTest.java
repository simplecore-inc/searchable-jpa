package dev.simplecore.searchable.core.service;

import dev.simplecore.searchable.core.condition.*;
import dev.simplecore.searchable.core.condition.operator.SearchOperator;
import dev.simplecore.searchable.core.service.bucket.TimeBucketCounter;
import dev.simplecore.searchable.test.config.BaseTestConfig;
import dev.simplecore.searchable.test.entity.AuditEvent;
import dev.simplecore.searchable.test.entity.AuditLabel;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.time.ZoneId;
import java.util.Map;
import dev.simplecore.searchable.core.service.bucket.TimeWindowParams;
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
    /**
     * A day in the app's time zone sliced by the hour, as the strip asks for it: each row lands in
     * the slice whose local hour holds it, and the next local midnight belongs to the next day.
     */
    @Test void aLocalDaySlicedByTheHourPutsEachRowInItsLocalHour() {
        ZoneId zone = ZoneId.of("Asia/Seoul");
        Instant from = LocalDate.of(2026, 11, 1).atStartOfDay(zone).toInstant();
        Instant to = LocalDate.of(2026, 11, 2).atStartOfDay(zone).toInstant();
        for (String local : List.of("00:00:00", "00:59:59.999", "01:00:00", "13:30:00", "13:31:00", "23:59:59")) {
            AuditEvent event = new AuditEvent();
            event.occurredAt = LocalDate.of(2026, 11, 1).atTime(LocalTime.parse(local)).atZone(zone).toInstant();
            em.persist(event);
        }
        AuditEvent nextDay = new AuditEvent(); nextDay.occurredAt = to; em.persist(nextDay);
        em.flush();
        var repo = new SimpleJpaRepository<AuditEvent, Long>(AuditEvent.class, em);
        List<Long> counts = new TimeBucketCounter(em).count(AuditEvent.class, repo, new SearchCondition<>(), "occurredAt", from, to, 24);
        Long[] expected = new Long[24];
        Arrays.fill(expected, 0L);
        expected[0] = 2L; expected[1] = 1L; expected[13] = 2L; expected[23] = 1L;
        assertThat(counts).containsExactly(expected);
    }
    @Test void theWindowParametersAreStrippedAndTheFiltersKept() {
        assertThat(TimeWindowParams.strip(Map.of("from", "a", "to", "b", "buckets", "24", "status.equals", "FAILED")))
                .containsExactly(Map.entry("status.equals", "FAILED"));
        assertThat(TimeWindowParams.strip(null)).isEmpty();
    }
}
