package dev.simplecore.searchable.test.config;

import dev.simplecore.searchable.test.fixture.TestDataManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;

import javax.annotation.PostConstruct;

@Slf4j
@TestConfiguration
@ComponentScan(basePackages = "dev.simplecore.searchable.test")
public class TestConfig {

    @Autowired
    private TestDataManager testDataManager;

    @PostConstruct
    public void initializeTestData() {
        log.info("Initializing test data via TestDataManager...");
        testDataManager.initializeTestData();
        log.info("Test data initialization complete");
    }
} 