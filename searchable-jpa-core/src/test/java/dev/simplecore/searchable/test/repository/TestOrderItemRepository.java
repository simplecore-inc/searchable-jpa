package dev.simplecore.searchable.test.repository;

import dev.simplecore.searchable.test.entity.TestOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface TestOrderItemRepository extends
        JpaRepository<TestOrderItem, TestOrderItem.OrderItemKey>,
        JpaSpecificationExecutor<TestOrderItem> {
}
