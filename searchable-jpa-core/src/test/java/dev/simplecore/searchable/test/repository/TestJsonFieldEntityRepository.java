package dev.simplecore.searchable.test.repository;

import dev.simplecore.searchable.test.entity.TestJsonFieldEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TestJsonFieldEntityRepository extends JpaRepository<TestJsonFieldEntity, Long>, JpaSpecificationExecutor<TestJsonFieldEntity> {
}