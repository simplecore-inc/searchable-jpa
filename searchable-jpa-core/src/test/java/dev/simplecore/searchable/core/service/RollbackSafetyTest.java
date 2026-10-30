package dev.simplecore.searchable.core.service;
import dev.simplecore.searchable.test.config.BaseTestConfig;
import dev.simplecore.searchable.test.entity.TestUpdatableEntity;
import dev.simplecore.searchable.test.repository.TestUpdatableEntityRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:rollback-control;DB_CLOSE_DELAY=-1", "spring.jpa.show-sql=false", "logging.level.org.hibernate.SQL=OFF"})
@ContextConfiguration(classes=BaseTestConfig.class)
public class RollbackSafetyTest {
    @Autowired TestUpdatableEntityRepository repo;
    @Autowired PlatformTransactionManager tm;
    @Test void rollbackSucceedsWithProviderGuaranteeDisabled() {
        assertThatThrownBy(()->new TransactionTemplate(tm).execute(s->{var e=new TestUpdatableEntity();e.setName("control");repo.saveAndFlush(e);throw new IllegalStateException();}));
        assertThat(repo.findAll()).isEmpty();
    }
}
