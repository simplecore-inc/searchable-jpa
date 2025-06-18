package dev.simplecore.searchable.test.service;

import dev.simplecore.searchable.core.service.DefaultSearchableService;
import dev.simplecore.searchable.test.entity.TestPost;
import dev.simplecore.searchable.test.repository.TestPostRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;

@Service
@Transactional
public class TestPostService extends DefaultSearchableService<TestPost, Long> {
    public TestPostService(TestPostRepository repository, EntityManager entityManager) {
        super(repository, entityManager);
    }

} 