package dev.simplecore.searchable.test.service;

import dev.simplecore.searchable.core.service.DefaultSearchableService;
import dev.simplecore.searchable.test.entity.TestOrderItem;
import dev.simplecore.searchable.test.repository.TestOrderItemRepository;
import org.springframework.stereotype.Service;

import jakarta.persistence.EntityManager;

@Service
public class TestOrderItemService extends DefaultSearchableService<TestOrderItem, TestOrderItem.OrderItemKey> {

    public TestOrderItemService(TestOrderItemRepository repository, EntityManager entityManager) {
        super(repository, entityManager);
    }
}
