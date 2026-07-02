package dev.simplecore.searchable.test.service;

import dev.simplecore.searchable.core.service.DefaultSearchableService;
import dev.simplecore.searchable.test.entity.TestUpdatableEntity;
import dev.simplecore.searchable.test.repository.TestUpdatableEntityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

@Service
@Transactional
public class TestUpdatableEntityService extends DefaultSearchableService<TestUpdatableEntity, Long> {

    public TestUpdatableEntityService(TestUpdatableEntityRepository repository, EntityManager entityManager) {
        super(repository, entityManager);
    }
}
