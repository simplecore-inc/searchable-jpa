package dev.simplecore.searchable.test.service;

import dev.simplecore.searchable.core.service.DefaultSearchableService;
import dev.simplecore.searchable.test.entity.TestJsonFieldEntity;
import dev.simplecore.searchable.test.repository.TestJsonFieldEntityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

@Service
@Transactional
public class TestJsonFieldEntityService extends DefaultSearchableService<TestJsonFieldEntity, Long> {
    public TestJsonFieldEntityService(TestJsonFieldEntityRepository repository, EntityManager entityManager) {
        super(repository, entityManager);
    }
}