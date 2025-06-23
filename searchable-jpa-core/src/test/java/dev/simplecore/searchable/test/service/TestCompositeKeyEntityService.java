package dev.simplecore.searchable.test.service;

import dev.simplecore.searchable.core.service.DefaultSearchableService;
import dev.simplecore.searchable.test.entity.TestCompositeKeyEntity;
import dev.simplecore.searchable.test.repository.TestCompositeKeyEntityRepository;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;

@Service
public class TestCompositeKeyEntityService extends DefaultSearchableService<TestCompositeKeyEntity, TestCompositeKeyEntity.CompositeKey> {

    public TestCompositeKeyEntityService(TestCompositeKeyEntityRepository repository, EntityManager entityManager) {
        super(repository, entityManager);
    }
} 