package dev.simplecore.searchable.test.fixture;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Test Data Manager
 * 
 * Handles different database environments:
 * - H2: Creates tables once per test session, loads small dataset
 * - Others: Creates tables if not exist, loads small dataset if tables are empty, preserves data between tests
 */
@Slf4j
@Component
public class TestDataManager {
    
    private static final String SMALL_DATASET_PATH = "test-data/small";
    private static final String[] TABLE_NAMES = {"test_author", "test_post", "test_comment", "test_tag", "test_post_tag"};
    private static final String[] CSV_FILES = {"test_author.csv", "test_post.csv", "test_comment.csv", "test_tag.csv", "test_post_tags.csv"};
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Autowired
    private DataSource dataSource;
    
    private static boolean h2DataInitialized = false;
    
    /**
     * Initialize test data based on database type
     */
    @Transactional
    public void initializeTestData() {
        try {
            DatabaseType dbType = detectDatabaseType();
            log.info("Detected database type: {}", dbType);
            
            if (dbType == DatabaseType.H2) {
                initializeH2Data();
            } else {
                initializeNonH2Data();
            }
            
        } catch (Exception e) {
            log.error("Failed to initialize test data", e);
            throw new RuntimeException("Test data initialization failed", e);
        }
    }
    
    /**
     * H2 database initialization - once per test session
     */
    private void initializeH2Data() {
        if (h2DataInitialized) {
            log.info("H2 test data already initialized, skipping...");
            return;
        }
        
        log.info("Initializing H2 test data...");
        
        // H2 auto-creates tables via JPA, just load data
        loadSmallDataset();
        h2DataInitialized = true;
        
        log.info("H2 test data initialization complete");
    }
    
    /**
     * Non-H2 database initialization - check data only (JPA handles table creation)
     */
    private void initializeNonH2Data() {
        log.info("Initializing non-H2 database...");
        
        // JPA handles table creation via ddl-auto setting
        // Just check if data exists and load if necessary
        if (isDataEmpty()) {
            log.info("Tables are empty, loading minimal test data...");
            loadSmallDataset();
        } else {
            log.info("Data already exists, skipping data load...");
        }
        
        log.info("Non-H2 database initialization complete");
    }
    
    /**
     * Detect database type
     */
    private DatabaseType detectDatabaseType() throws SQLException {
        DatabaseMetaData metaData = dataSource.getConnection().getMetaData();
        String databaseProductName = metaData.getDatabaseProductName().toLowerCase();
        
        if (databaseProductName.contains("h2")) {
            return DatabaseType.H2;
        } else if (databaseProductName.contains("microsoft") || databaseProductName.contains("sql server")) {
            return DatabaseType.MSSQL;
        } else if (databaseProductName.contains("mysql")) {
            return DatabaseType.MYSQL;
        } else if (databaseProductName.contains("postgresql")) {
            return DatabaseType.POSTGRESQL;
        } else if (databaseProductName.contains("oracle")) {
            return DatabaseType.ORACLE;
        } else {
            return DatabaseType.OTHER;
        }
    }
    

    
    /**
     * Check if data is empty (check main tables)
     */
    private boolean isDataEmpty() {
        try {
            Integer authorCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM test_author", Integer.class);
            Integer postCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM test_post", Integer.class);
            
            log.info("Data check - Authors: {}, Posts: {}", authorCount, postCount);
            
            boolean isEmpty = (authorCount == null || authorCount == 0) && (postCount == null || postCount == 0);
            
            if (!isEmpty) {
                log.info("Existing data found - Authors: {}, Posts: {}. Skipping test data initialization.", 
                    authorCount, postCount);
            }
            
            return isEmpty;
        } catch (Exception e) {
            log.warn("Error checking data existence, assuming empty: {}", e.getMessage());
            return true;
        }
    }
    

    /**
     * Load small dataset from CSV files
     */
    private void loadSmallDataset() {
        log.info("Loading small dataset...");
        
        try {
            // Load in dependency order
            loadCsvData("test_author.csv", "test_author");
            loadCsvData("test_tag.csv", "test_tag");
            loadCsvData("test_post.csv", "test_post");
            loadCsvData("test_comment.csv", "test_comment");
            loadCsvData("test_post_tags.csv", "test_post_tag");
            
            log.info("Small dataset loaded successfully");
        } catch (IOException | SQLException e) {
            log.error("Failed to load small dataset", e);
            throw new RuntimeException("Data loading failed", e);
        }
    }
    
    /**
     * Load CSV data into table using database-specific CSV import
     */
    private void loadCsvData(String csvFileName, String tableName) throws IOException, SQLException {
        ClassPathResource resource = new ClassPathResource(SMALL_DATASET_PATH + "/" + csvFileName);
        
        if (!resource.exists()) {
            log.warn("CSV file not found: {}", csvFileName);
            return;
        }
        
        DatabaseType dbType = detectDatabaseType();
        
        try {
            switch (dbType) {
                            case H2:
                loadCsvDataH2(resource, tableName);
                break;
            case MSSQL:
                loadCsvDataMSSQL(resource, tableName);
                break;
            case MYSQL:
                loadCsvDataMySQL(resource, tableName);
                break;
            case POSTGRESQL:
                loadCsvDataPostgreSQL(resource, tableName);
                break;
            case ORACLE:
                loadCsvDataOracle(resource, tableName);
                break;
            default:
                // Unknown database type
                log.error("Unknown database type, CSV direct loading is not supported for {}", tableName);
                throw new RuntimeException("CSV direct loading is not supported for unknown database type. Table: " + tableName);
            }
            
            // Count loaded records
            Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
            log.info("Loaded {} records into {}", count != null ? count : 0, tableName);
            
        } catch (Exception e) {
            log.error("Failed to load CSV data for {}: {}", tableName, e.getMessage());
            throw new RuntimeException("CSV loading failed for " + tableName, e);
        }
    }
    
    /**
     * Load CSV data for H2 database
     */
    private void loadCsvDataH2(ClassPathResource resource, String tableName) throws IOException {
        try {
            // First try direct file access
            String csvPath = resource.getFile().getAbsolutePath();
            String sql = buildH2CsvInsertQuery(tableName, csvPath);
            jdbcTemplate.execute(sql);
            log.info("H2: Loaded CSV data for {} using direct file access", tableName);
            
        } catch (Exception e) {
            log.warn("H2: Direct file access failed, trying classpath resource method: {}", e.getMessage());
            
            try {
                // Try using classpath URL
                String csvUrl = resource.getURL().toString();
                String sql = buildH2CsvInsertQuery(tableName, csvUrl);
                jdbcTemplate.execute(sql);
                log.info("H2: Loaded CSV data for {} using classpath URL", tableName);
                
            } catch (Exception e2) {
                // If both methods fail, throw exception
                log.error("H2: All CSV loading methods failed for {}", tableName);
                throw new RuntimeException("Failed to load CSV data for " + tableName + " in H2", e2);
            }
        }
    }
    
    /**
     * Build H2 CSV insert query with proper column mapping
     */
    private String buildH2CsvInsertQuery(String tableName, String csvPath) {
        switch (tableName) {
            case "test_author":
                return String.format(
                    "INSERT INTO %s (author_id, name, email, nickname, description, created_at, updated_at, created_by, updated_by) " +
                    "SELECT * FROM CSVREAD('%s', NULL, 'charset=UTF-8 fieldSeparator=,')", 
                    tableName, csvPath
                );
            case "test_post":
                return String.format(
                    "INSERT INTO %s (post_id, title, content, status, view_count, like_count, author_id, created_at, updated_at, created_by, updated_by) " +
                    "SELECT * FROM CSVREAD('%s', NULL, 'charset=UTF-8 fieldSeparator=,')", 
                    tableName, csvPath
                );
            case "test_tag":
                return String.format(
                    "INSERT INTO %s (tag_id, name, description, color, created_at, updated_at, created_by, updated_by) " +
                    "SELECT * FROM CSVREAD('%s', NULL, 'charset=UTF-8 fieldSeparator=,')", 
                    tableName, csvPath
                );
            case "test_comment":
                return String.format(
                    "INSERT INTO %s (comment_id, content, author_id, post_id, created_at, updated_at, created_by, updated_by) " +
                    "SELECT * FROM CSVREAD('%s', NULL, 'charset=UTF-8 fieldSeparator=,')", 
                    tableName, csvPath
                );
            case "test_post_tag":
                return String.format(
                    "INSERT INTO %s (post_id, tag_id) " +
                    "SELECT * FROM CSVREAD('%s', NULL, 'charset=UTF-8 fieldSeparator=,')", 
                    tableName, csvPath
                );
            default:
                // Generic fallback
                return String.format(
                    "INSERT INTO %s SELECT * FROM CSVREAD('%s', NULL, 'charset=UTF-8 fieldSeparator=,')", 
                    tableName, csvPath
                );
        }
    }
    
    /**
     * Load CSV data for MSSQL database
     */
    private void loadCsvDataMSSQL(ClassPathResource resource, String tableName) throws IOException {
        log.info("MSSQL: Loading CSV data for {} using row-by-row INSERT", tableName);
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream()))) {
            String line;
            boolean isFirstLine = true;
            int batchSize = 1000;
            int batchCount = 0;
            List<String> batchStatements = new ArrayList<>();
            
            // Handle IDENTITY_INSERT for tables with auto-increment columns
            boolean needsIdentityInsert = needsIdentityInsert(tableName);
            if (needsIdentityInsert) {
                jdbcTemplate.execute("SET IDENTITY_INSERT " + tableName + " ON");
            }
            
            while ((line = reader.readLine()) != null) {
                if (isFirstLine) {
                    isFirstLine = false;
                    continue; // Skip header
                }
                
                if (line.trim().isEmpty()) {
                    continue;
                }
                
                String insertSql = buildInsertStatement(tableName, line);
                if (insertSql != null) {
                    batchStatements.add(insertSql);
                    batchCount++;
                    
                    if (batchCount >= batchSize) {
                        executeBatch(batchStatements);
                        batchStatements.clear();
                        batchCount = 0;
                        log.info("MSSQL: Processed {} records for {}", batchSize, tableName);
                    }
                }
            }
            
            // Execute remaining statements
            if (!batchStatements.isEmpty()) {
                executeBatch(batchStatements);
                log.info("MSSQL: Processed final {} records for {}", batchStatements.size(), tableName);
            }
            
            // Turn off IDENTITY_INSERT
            if (needsIdentityInsert) {
                jdbcTemplate.execute("SET IDENTITY_INSERT " + tableName + " OFF");
            }
            
            log.info("MSSQL: Successfully loaded CSV data for {}", tableName);
            
        } catch (Exception e) {
            log.error("MSSQL: Failed to load CSV data for {}: {}", tableName, e.getMessage());
            throw new RuntimeException("Failed to load CSV data for " + tableName + " in MSSQL", e);
        }
    }
    
    /**
     * Check if table needs IDENTITY_INSERT
     */
    private boolean needsIdentityInsert(String tableName) {
        return !tableName.equals("test_post_tag"); // test_post_tag doesn't have auto-increment
    }
    
    /**
     * Build INSERT statement for MSSQL
     */
    private String buildInsertStatement(String tableName, String csvLine) {
        Object[] values = parseCsvLine(csvLine);
        
        switch (tableName) {
            case "test_author":
                if (values.length >= 9) {
                    return String.format(
                        "INSERT INTO test_author (author_id, name, email, nickname, description, created_at, updated_at, created_by, updated_by) " +
                        "VALUES (%s, '%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s')",
                        values[0],
                        escapeSqlString(values[1].toString()),
                        escapeSqlString(values[2].toString()),
                        escapeSqlString(values[3].toString()),
                        escapeSqlString(values[4].toString()),
                        values[5], values[6],
                        escapeSqlString(values[7].toString()),
                        escapeSqlString(values[8].toString())
                    );
                }
                break;
            case "test_post":
                if (values.length >= 11) {
                    return String.format(
                        "INSERT INTO test_post (post_id, title, content, status, view_count, like_count, author_id, created_at, updated_at, created_by, updated_by) " +
                        "VALUES (%s, '%s', '%s', '%s', %s, %s, %s, '%s', '%s', '%s', '%s')",
                        values[0],
                        escapeSqlString(values[1].toString()),
                        escapeSqlString(values[2].toString()),
                        escapeSqlString(values[3].toString()),
                        values[4], values[5], values[6],
                        values[7], values[8],
                        escapeSqlString(values[9].toString()),
                        escapeSqlString(values[10].toString())
                    );
                }
                break;
            case "test_tag":
                if (values.length >= 8) {
                    return String.format(
                        "INSERT INTO test_tag (tag_id, name, description, color, created_at, updated_at, created_by, updated_by) " +
                        "VALUES (%s, '%s', '%s', '%s', '%s', '%s', '%s', '%s')",
                        values[0],
                        escapeSqlString(values[1].toString()),
                        escapeSqlString(values[2].toString()),
                        escapeSqlString(values[3].toString()),
                        values[4], values[5],
                        escapeSqlString(values[6].toString()),
                        escapeSqlString(values[7].toString())
                    );
                }
                break;
            case "test_comment":
                if (values.length >= 8) {
                    return String.format(
                        "INSERT INTO test_comment (comment_id, content, author_id, post_id, created_at, updated_at, created_by, updated_by) " +
                        "VALUES (%s, '%s', %s, %s, '%s', '%s', '%s', '%s')",
                        values[0],
                        escapeSqlString(values[1].toString()),
                        values[2], values[3],
                        values[4], values[5],
                        escapeSqlString(values[6].toString()),
                        escapeSqlString(values[7].toString())
                    );
                }
                break;
            case "test_post_tag":
                if (values.length >= 2) {
                    return String.format(
                        "INSERT INTO test_post_tag (post_id, tag_id) VALUES (%s, %s)",
                        values[0], values[1]
                    );
                }
                break;
        }
        
        log.warn("MSSQL: Unable to build INSERT statement for {} with {} values", tableName, values.length);
        return null;
    }
    
    /**
     * Escape SQL string values and convert \n back to actual newlines
     */
    private String escapeSqlString(String value) {
        if (value == null) {
            return "";
        }
        // Convert \n string representation back to actual newlines
        value = value.replace("\\n", "\n");
        value = value.replace("\\r", "\r");
        
        return value.replace("'", "''");
    }
    
    /**
     * Execute batch statements
     */
    private void executeBatch(List<String> statements) {
        for (String sql : statements) {
            try {
                jdbcTemplate.execute(sql);
            } catch (Exception e) {
                log.warn("MSSQL: Failed to execute statement: {} - Error: {}", sql, e.getMessage());
            }
        }
    }
    
    /**
     * Load CSV data for MySQL database
     */
    private void loadCsvDataMySQL(ClassPathResource resource, String tableName) throws IOException {
        try {
            String csvPath = resource.getFile().getAbsolutePath();
            String sql = String.format(
                "LOAD DATA INFILE '%s' INTO TABLE %s FIELDS TERMINATED BY ',' LINES TERMINATED BY '\\n' IGNORE 1 ROWS",
                csvPath, tableName
            );
            jdbcTemplate.execute(sql);
            log.info("MySQL: Loaded CSV data for {} using LOAD DATA INFILE", tableName);
        } catch (Exception e) {
            log.error("MySQL: LOAD DATA INFILE failed for {}: {}", tableName, e.getMessage());
            throw new RuntimeException("Failed to load CSV data for " + tableName + " in MySQL", e);
        }
    }
    
    /**
     * Load CSV data for PostgreSQL database
     */
    private void loadCsvDataPostgreSQL(ClassPathResource resource, String tableName) throws IOException {
        try {
            String csvPath = resource.getFile().getAbsolutePath();
            String sql = String.format(
                "COPY %s FROM '%s' WITH (FORMAT csv, HEADER true)",
                tableName, csvPath
            );
            jdbcTemplate.execute(sql);
            log.info("PostgreSQL: Loaded CSV data for {} using COPY", tableName);
        } catch (Exception e) {
            log.error("PostgreSQL: COPY command failed for {}: {}", tableName, e.getMessage());
            throw new RuntimeException("Failed to load CSV data for " + tableName + " in PostgreSQL", e);
        }
    }
    
    /**
     * Load CSV data for Oracle database
     */
    private void loadCsvDataOracle(ClassPathResource resource, String tableName) throws IOException {
        // Oracle doesn't have a simple CSV import functionality like other databases
        log.error("Oracle: CSV direct loading is not supported for {}", tableName);
        throw new RuntimeException("CSV direct loading is not supported for Oracle database. Please use a different approach for " + tableName);
    }
    
    
    
    /**
     * Parse CSV line into values
     */
    private Object[] parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder current = new StringBuilder();
        
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++; // Skip next quote
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                values.add(current.toString().trim());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        values.add(current.toString().trim());
        
        return values.toArray();
    }
    
    /**
     * Database type enumeration
     */
    private enum DatabaseType {
        H2, MSSQL, MYSQL, POSTGRESQL, ORACLE, OTHER
    }
} 