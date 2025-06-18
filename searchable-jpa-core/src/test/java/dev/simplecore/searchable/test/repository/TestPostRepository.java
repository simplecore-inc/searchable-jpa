package dev.simplecore.searchable.test.repository;

import dev.simplecore.searchable.test.entity.TestPost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TestPostRepository extends JpaRepository<TestPost, Long>, JpaSpecificationExecutor<TestPost> {
} 