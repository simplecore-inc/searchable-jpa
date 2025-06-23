package dev.simplecore.searchable.test.repository;

import dev.simplecore.searchable.test.entity.TestCompositeKeyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface TestCompositeKeyEntityRepository extends 
    JpaRepository<TestCompositeKeyEntity, TestCompositeKeyEntity.CompositeKey>, 
    JpaSpecificationExecutor<TestCompositeKeyEntity> {
} 