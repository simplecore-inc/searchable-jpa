package dev.simplecore.searchable.test.repository;

import dev.simplecore.searchable.test.entity.TestIdClassEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface TestIdClassEntityRepository extends 
    JpaRepository<TestIdClassEntity, TestIdClassEntity.CompositeKey>, 
    JpaSpecificationExecutor<TestIdClassEntity> {
} 