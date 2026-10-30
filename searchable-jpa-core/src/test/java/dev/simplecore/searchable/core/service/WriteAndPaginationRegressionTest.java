package dev.simplecore.searchable.core.service;

import dev.simplecore.searchable.core.condition.*;
import dev.simplecore.searchable.core.condition.operator.*;
import dev.simplecore.searchable.core.service.*;
import dev.simplecore.searchable.test.config.BaseTestConfig;
import dev.simplecore.searchable.test.dto.TestUpdatableSearchDTO;
import dev.simplecore.searchable.test.entity.TestUpdatableEntity;
import dev.simplecore.searchable.test.repository.TestUpdatableEntityRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:audit;DB_CLOSE_DELAY=-1", "spring.jpa.show-sql=false", "logging.level.org.hibernate.SQL=OFF"})
@ContextConfiguration(classes=BaseTestConfig.class)
public class WriteAndPaginationRegressionTest {
    @Autowired EntityManager em;
    @Autowired TestUpdatableEntityRepository repo;
    @Autowired PlatformTransactionManager tm;
    SearchableServiceDelegate<TestUpdatableEntity,Long> service(){ return new SearchableServiceDelegate<>(repo,em,TestUpdatableEntity.class); }
    TestUpdatableEntity row(String name,String status) {var e=new TestUpdatableEntity();e.setName(name);e.setStatus(status);return repo.saveAndFlush(e);}
    @Test void updateMustNotChangePrimaryKey() {
        new TransactionTemplate(tm).executeWithoutResult(s->{
            var e=row("pk-audit","ACTIVE");Long old=e.getId();
            var c=SearchConditionBuilder.create(TestUpdatableSearchDTO.class).where(w->w.equals("name","pk-audit")).build();
            var patch=new TestUpdatableEntity();patch.setId(old+10000);patch.setStatus("NEW");
            service().updateWithSearch(c,patch);em.flush();
            assertThat(e.getId()).isEqualTo(old);
        });
    }
    @Test void firstShouldIgnorePageOffset() {
        new TransactionTemplate(tm).executeWithoutResult(s->{
            row("only-first-audit","ACTIVE");
            var c=SearchConditionBuilder.create(TestUpdatableSearchDTO.class).where(w->w.equals("name","only-first-audit")).page(10).size(20).build();
            assertThat(service().findFirstWithSearch(c)).isPresent();
        });
    }
    @Test void writesVisitEveryRowWhenTheFilterChanges() {
        new TransactionTemplate(tm).executeWithoutResult(tx -> {
            for (int i = 0; i < 450; i++) row("chunk-" + i, "CHUNK");
        });
        var filter = SearchConditionBuilder.create(TestUpdatableSearchDTO.class).where(w -> w.equals("status", "CHUNK")).build();
        var patch = new TestUpdatableEntity(); patch.setStatus("CHANGED");
        assertThat(service().updateWithSearch(filter, patch)).isEqualTo(450);
        assertThat(service().countWithSearch(filter)).isZero();
        var changed = SearchConditionBuilder.create(TestUpdatableSearchDTO.class).where(w -> w.equals("status", "CHANGED")).build();
        assertThat(service().deleteWithSearch(changed)).isEqualTo(450);
        assertThat(service().countWithSearch(changed)).isZero();
    }
    @Test void excessivePageSizeAndOverflowAreRejectedBeforeSql() {
        var c = new SearchCondition<>(); c.setPage(0); c.setSize(Integer.MAX_VALUE);
        assertThatThrownBy(() -> service().findAllWithSearch(c)).hasMessageContaining("size");
        c.setSize(20); c.setPage(Integer.MAX_VALUE);
        assertThatThrownBy(() -> service().findAllWithSearch(c)).hasMessageContaining("offset");
    }
    public static class NamePatch {
        private String name;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }
    @Test void narrowDtoDoesNotResetPrimitiveFieldsOrCopyVersion() {
        new TransactionTemplate(tm).executeWithoutResult(tx -> {
            var e = row("primitive-audit", "PRIMITIVE"); e.setQuantity(37); repo.flush();
            var filter = SearchConditionBuilder.create(TestUpdatableSearchDTO.class).where(w -> w.equals("status", "PRIMITIVE")).build();
            NamePatch patch = new NamePatch(); patch.setName("renamed");
            assertThat(service().updateWithSearch(filter, patch)).isEqualTo(1);
            assertThat(e.getQuantity()).isEqualTo(37);
            var entityPatch = new TestUpdatableEntity(); entityPatch.setRevision(999L); entityPatch.setName("again");
            service().updateWithSearch(filter, entityPatch);
            assertThat(e.getRevision()).isNotEqualTo(999L);
        });
    }
}
