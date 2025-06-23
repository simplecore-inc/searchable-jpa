package dev.simplecore.searchable.test.service;

import dev.simplecore.searchable.core.service.DefaultSearchableService;
import dev.simplecore.searchable.test.entity.TestIdClassEntity;
import dev.simplecore.searchable.test.repository.TestIdClassEntityRepository;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;

@Service
public class TestIdClassEntityService extends DefaultSearchableService<TestIdClassEntity, TestIdClassEntity.CompositeKey> {

    public TestIdClassEntityService(TestIdClassEntityRepository repository, EntityManager entityManager) {
        super(repository, entityManager);
    }
} 