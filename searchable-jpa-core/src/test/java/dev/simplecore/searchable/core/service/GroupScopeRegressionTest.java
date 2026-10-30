package dev.simplecore.searchable.core.service;

import dev.simplecore.searchable.core.condition.*;
import dev.simplecore.searchable.core.condition.operator.*;
import dev.simplecore.searchable.core.service.*;
import dev.simplecore.searchable.test.config.BaseTestConfig;
import dev.simplecore.searchable.test.entity.TestUpdatableEntity;
import dev.simplecore.searchable.test.dto.TestUpdatableSearchDTO;
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
public class GroupScopeRegressionTest {
    @Autowired EntityManager em;
    @Autowired TestUpdatableEntityRepository repo;
    @Autowired PlatformTransactionManager tm;
    SearchableServiceDelegate<TestUpdatableEntity,Long> service(){ return new SearchableServiceDelegate<>(repo,em,TestUpdatableEntity.class); }
    TestUpdatableEntity row(String name,String status) {var e=new TestUpdatableEntity();e.setName(name);e.setStatus(status);return repo.saveAndFlush(e);}
    @Test void andGroupShouldPreserveSecurityPredicate() {
        new TransactionTemplate(tm).executeWithoutResult(s->{
            var e=row("outside-tenant","OTHER");
            var c=SearchConditionBuilder.create(TestUpdatableSearchDTO.class)
                .where(w->w.equals("status","ACTIVE"))
                .and(w->w.equals("name","allowed").orEquals("name","outside-tenant")).build();
            assertThat(service().findAllWithSearch(c).getContent()).noneMatch(x->x.getId().equals(e.getId()));
        });
    }
}
