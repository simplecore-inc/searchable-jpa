package dev.simplecore.searchable.test.repository;

import dev.simplecore.searchable.test.entity.TestUpdatableEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface TestUpdatableEntityRepository extends
        JpaRepository<TestUpdatableEntity, Long>,
        JpaSpecificationExecutor<TestUpdatableEntity> {
}
