package dev.simplecore.searchable.test.fixture;

import dev.simplecore.searchable.test.enums.TestPostStatus;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * CSV Test Data Generator Utility
 * 
 * Features:
 * - Uses fixed seed to generate consistent data (except date/time)
 * - Can be executed independently of tests
 * - Supports 10K, 5M, and 10M dataset sizes
 * - Generates data considering entity relationships
 * - Ensures same first N records across different dataset sizes for consistent testing
 * 
 * gradlew generateLargeData
 */
public class CsvTestDataGenerator {
    
    private static final long FIXED_SEED = 12345L; // Fixed seed for deterministic data generation
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    // Base date for consistent date generation across datasets
    private static final LocalDateTime BASE_DATE = LocalDateTime.of(2023, 1, 1, 0, 0, 0);
    
    // Fixed arrays for data generation
    private static final String[] FIRST_NAMES = {
        "James", "John", "Robert", "Michael", "William", "David", "Richard", "Charles", "Joseph", "Thomas",
        "Christopher", "Daniel", "Paul", "Mark", "Donald", "Steven", "Kenneth", "Andrew", "Joshua", "Kevin",
        "Brian", "George", "Timothy", "Ronald", "Jason", "Edward", "Jeffrey", "Ryan", "Jacob", "Gary",
        "Nicholas", "Eric", "Jonathan", "Stephen", "Larry", "Justin", "Scott", "Brandon", "Benjamin", "Samuel"
    };
    
    private static final String[] LAST_NAMES = {
        "Smith", "Johnson", "Williams", "Brown", "Jones", "Garcia", "Miller", "Davis", "Rodriguez", "Martinez",
        "Hernandez", "Lopez", "Gonzalez", "Wilson", "Anderson", "Thomas", "Taylor", "Moore", "Jackson", "Martin",
        "Lee", "Perez", "Thompson", "White", "Harris", "Sanchez", "Clark", "Ramirez", "Lewis", "Robinson"
    };
    
    private static final String[] POST_TITLE_TEMPLATES = {
        "Understanding {}", "The Future of {}", "Efficient Ways to Use {}", "Lessons from {}",
        "New Approaches to {}", "Latest Trends in {}", "The World Through {}", "Practical Applications of {}",
        "Expert Opinions on {}", "Advantages and Limitations of {}", "Practical Guide to {}", "Innovative Uses of {}"
    };
    
    private static final String[] TOPICS = {
        "Spring Framework", "React", "Vue.js", "Angular", "Node.js", "Python", "Java", "JavaScript",
        "TypeScript", "Docker", "Kubernetes", "AWS", "Azure", "GCP", "DevOps", "CI/CD",
        "Machine Learning", "AI", "Data Science", "Big Data", "Blockchain", "Microservices",
        "Clean Code", "Design Patterns", "Architecture", "Testing", "Security", "Performance"
    };
    
    private static final String[] TAG_NAMES = {
        "Java", "Spring", "JPA", "Hibernate", "React", "Vue", "Angular", "JavaScript", "TypeScript",
        "Python", "Machine Learning", "AI", "DevOps", "Docker", "Kubernetes", "AWS", "Azure",
        "Microservices", "API", "Database", "MongoDB", "PostgreSQL", "MySQL", "Redis", "Elasticsearch",
        "Security", "Testing", "Performance", "Clean Code", "Architecture", "Design Patterns"
    };
    
    private static final String[] COMMENT_TEMPLATES = {
        "This is a really helpful article! Thank you.", "Thanks for sharing this valuable information.", "Interesting perspective. I learned something new.",
        "I think I can apply this to my work right away.", "Thank you for the detailed explanation.", "Looking forward to your next post.",
        "I have a question about this...", "Could you explain this part in more detail?", "I have a different opinion on this.",
        "Great article based on real experience.", "I can relate to this from my own experience.", "It would be great to have some reference materials too."
    };

    private final String outputDir;
    
    public CsvTestDataGenerator(String outputDir) {
        this.outputDir = outputDir;
        
        // Create output directory
        File dir = new File(outputDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }
    
    /**
     * Generates test data with the specified number of posts.
     */
    public void generateTestData(int postCount) throws IOException {
        System.out.printf("=== Starting %,d Test Data Generation ===\n", postCount);
        
        // Calculate entity counts
        int authorCount = Math.max(100, postCount / 50); // 1 author per 50 posts
        int tagCount = Math.min(100, TAG_NAMES.length); // Maximum 100 tags
        int commentCount = postCount * 3; // Average 3 comments per post
        
        System.out.printf("Authors: %,d, Tags: %,d, Posts: %,d, Comments: %,d\n", 
                         authorCount, tagCount, postCount, commentCount);
        
        // Generate data
        generateAuthors(authorCount);
        generateTags(tagCount);
        generatePosts(postCount, authorCount);
        generateComments(commentCount, postCount, authorCount);
        generatePostTags(postCount, tagCount);
        
        System.out.println("=== Data Generation Complete ===");
    }
    
    /**
     * Generate author data with deterministic sequence
     */
    private void generateAuthors(int count) throws IOException {
        String filename = outputDir + "/test_author.csv";
        System.out.printf("Generating author data... (%s)\n", filename);
        
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
            // CSV header
            writer.write("author_id,name,email,nickname,description,created_at,updated_at,created_by,updated_by\n");
            
            for (int i = 1; i <= count; i++) {
                String name = generateDeterministicName(i);
                String email = generateDeterministicEmail(name, i);
                String nickname = generateDeterministicNickname(name, i);
                String description = String.format("Profile of %s", name);
                LocalDateTime createdAt = generateDeterministicDate(i, 365);
                
                writer.write(String.format("%d,%s,%s,%s,%s,%s,%s,system,system\n",
                    i, name, email, nickname, escapeCSV(description),
                    createdAt.format(DATE_FORMATTER), createdAt.format(DATE_FORMATTER)));
                
                if (i % 10000 == 0) {
                    System.out.printf("  Generated %,d authors\n", i);
                }
            }
        }
    }
    
    /**
     * Generate tag data with deterministic sequence
     */
    private void generateTags(int count) throws IOException {
        String filename = outputDir + "/test_tag.csv";
        System.out.printf("Generating tag data... (%s)\n", filename);
        
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
            // CSV header
            writer.write("tag_id,name,description,color,created_at,updated_at,created_by,updated_by\n");
            
            for (int i = 1; i <= count; i++) {
                String name = TAG_NAMES[(i - 1) % TAG_NAMES.length];
                String description = String.format("Posts related to %s", name);
                String color = generateDeterministicColor(i);
                LocalDateTime createdAt = generateDeterministicDate(i, 30);
                LocalDateTime updatedAt = createdAt;
                
                writer.write(String.format("%d,%s,%s,%s,%s,%s,system,system\n",
                    i, name, escapeCSV(description), color, 
                    createdAt.format(DATE_FORMATTER), updatedAt.format(DATE_FORMATTER)));
            }
        }
    }
    
    /**
     * Generate post data with deterministic sequence
     */
    private void generatePosts(int count, int authorCount) throws IOException {
        String filename = outputDir + "/test_post.csv";
        System.out.printf("Generating post data... (%s)\n", filename);
        
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
            // CSV header
            writer.write("post_id,title,content,status,view_count,like_count,author_id,created_at,updated_at,created_by,updated_by\n");
            
            for (int i = 1; i <= count; i++) {
                String topic = generateDeterministicTopic(i);
                String titleTemplate = generateDeterministicTitleTemplate(i);
                String title = titleTemplate.replace("{}", topic);
                String content = generatePostContent(topic);
                TestPostStatus status = generateDeterministicStatus(i);
                long viewCount = generateDeterministicViewCount(i);
                long likeCount = Math.max(1, viewCount / 10 + (i % 5));
                long authorId = (i % authorCount) + 1;
                LocalDateTime createdAt = generateDeterministicDate(i, 90);
                LocalDateTime updatedAt = createdAt.plusHours(i % 24);
                
                writer.write(String.format("%d,%s,%s,%s,%d,%d,%d,%s,%s,system,system\n",
                    i, escapeCSV(title), escapeCSV(content), status,
                    viewCount, likeCount, authorId,
                    createdAt.format(DATE_FORMATTER), updatedAt.format(DATE_FORMATTER)));
                
                if (i % 10000 == 0) {
                    System.out.printf("  Generated %,d posts\n", i);
                }
            }
        }
    }
    
    /**
     * Generate comment data with deterministic sequence
     */
    private void generateComments(int count, int postCount, int authorCount) throws IOException {
        String filename = outputDir + "/test_comment.csv";
        System.out.printf("Generating comment data... (%s)\n", filename);
        
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
            // CSV header
            writer.write("comment_id,content,author_id,post_id,created_at,updated_at,created_by,updated_by\n");
            
            for (int i = 1; i <= count; i++) {
                String content = generateDeterministicCommentContent(i);
                long authorId = (i % authorCount) + 1;
                long postId = (i % postCount) + 1;
                LocalDateTime createdAt = generateDeterministicDate(i, 30);
                LocalDateTime updatedAt = createdAt;
                
                writer.write(String.format("%d,%s,%d,%d,%s,%s,system,system\n",
                    i, escapeCSV(content), authorId, postId,
                    createdAt.format(DATE_FORMATTER), updatedAt.format(DATE_FORMATTER)));
                
                if (i % 50000 == 0) {
                    System.out.printf("  Generated %,d comments\n", i);
                }
            }
        }
    }
    
    /**
     * Generate post-tag relationship data with deterministic sequence
     */
    private void generatePostTags(int postCount, int tagCount) throws IOException {
        String filename = outputDir + "/test_post_tags.csv";
        System.out.printf("Generating post-tag relationship data... (%s)\n", filename);
        
        Set<String> generatedPairs = new HashSet<>();
        
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
            // CSV header
            writer.write("post_id,tag_id\n");
            
            for (int postId = 1; postId <= postCount; postId++) {
                // Deterministic tag assignment: 1-5 tags per post based on post ID
                int tagAssignCount = (postId % 5) + 1;
                Set<Integer> assignedTags = new HashSet<>();
                
                for (int j = 0; j < tagAssignCount; j++) {
                    int tagId = ((postId * 7 + j * 3) % tagCount) + 1; // Deterministic tag selection
                    if (assignedTags.add(tagId)) {
                        String pair = postId + "," + tagId;
                        if (generatedPairs.add(pair)) {
                            writer.write(pair + "\n");
                        }
                    }
                }
                
                if (postId % 100000 == 0) {
                    System.out.printf("  Processed %,d post-tag relationships\n", postId);
                }
            }
        }
        
        System.out.printf("  Generated total %,d post-tag relationships\n", generatedPairs.size());
    }
    
    // === Deterministic Generation Methods ===
    
    private String generateDeterministicName(int index) {
        String firstName = FIRST_NAMES[(index - 1) % FIRST_NAMES.length];
        String lastName = LAST_NAMES[((index - 1) / FIRST_NAMES.length) % LAST_NAMES.length];
        return firstName + " " + lastName;
    }
    
    private String generateDeterministicEmail(String name, int index) {
        String[] domains = {"gmail.com", "yahoo.com", "outlook.com", "hotmail.com", "example.com"};
        String domain = domains[(index - 1) % domains.length];
        String cleanName = name.toLowerCase().replace(" ", ".");
        return String.format("user%d.%s@%s", index, cleanName, domain);
    }
    
    private String generateDeterministicNickname(String name, int index) {
        String[] suffixes = {"_dev", "_pro", "_master", "_expert", "_ninja", "_guru", "123", "456"};
        String suffix = suffixes[(index - 1) % suffixes.length];
        String cleanName = name.replace(" ", "");
        return cleanName + suffix;
    }
    
    private String generateDeterministicColor(int index) {
        String[] colors = {"#FF5733", "#33FF57", "#3357FF", "#FF33F1", "#F1FF33", "#33FFF1"};
        return colors[(index - 1) % colors.length];
    }
    
    private String generateDeterministicTopic(int index) {
        return TOPICS[(index - 1) % TOPICS.length];
    }
    
    private String generateDeterministicTitleTemplate(int index) {
        return POST_TITLE_TEMPLATES[(index - 1) % POST_TITLE_TEMPLATES.length];
    }
    
    private TestPostStatus generateDeterministicStatus(int index) {
        TestPostStatus[] statuses = TestPostStatus.values();
        return statuses[(index - 1) % statuses.length];
    }
    
    private long generateDeterministicViewCount(int index) {
        // Generate view count based on index: 1-10000 range
        return ((index * 137) % 10000) + 1;
    }
    
    private LocalDateTime generateDeterministicDate(int index, int maxDaysBack) {
        // Generate deterministic date within maxDaysBack from BASE_DATE
        int daysBack = (index * 17) % maxDaysBack;
        int hours = (index * 7) % 24;
        int minutes = (index * 3) % 60;
        return BASE_DATE.minusDays(daysBack).plusHours(hours).plusMinutes(minutes);
    }
    
    private String generateDeterministicCommentContent(int index) {
        String template = COMMENT_TEMPLATES[(index - 1) % COMMENT_TEMPLATES.length];
        if (index % 2 == 0) {
            return template;
        } else {
            return template + " I also have additional thoughts on this.";
        }
    }
    
    // === Utility Methods ===
    
    private String generatePostContent(String topic) {
        StringBuilder content = new StringBuilder();
        content.append(String.format("This is detailed content about %s.\n\n", topic));
        content.append("This article covers the following topics:\n");
        content.append("1. Basic concepts and theory\n");
        content.append("2. Practical application methods\n");
        content.append("3. Best practices\n");
        content.append("4. Important considerations\n\n");
        content.append("Please refer to the main content for detailed information.");
        return content.toString();
    }
    
    private String escapeCSV(String value) {
        if (value == null) return "";
        
        // Replace actual newlines with \n string representation
        value = value.replace("\n", "\\n");
        value = value.replace("\r", "\\r");
        
        if (value.contains("\"") || value.contains(",")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
    
    /**
     * Generate only small dataset
     */
    public static void generateSmallDatasetOnly() {
        try {
            String outputDir = "src/test/resources/test-data";
            String datasetDir = outputDir + "/small";
            CsvTestDataGenerator generator = new CsvTestDataGenerator(datasetDir);
            
            System.out.printf("========================================\n");
            System.out.printf("Generating Small Dataset (10,000 records)\n");
            System.out.printf("========================================\n");
            
            long startTime = System.currentTimeMillis();
            generator.generateTestData(10_000);
            long endTime = System.currentTimeMillis();
            
            System.out.printf("Elapsed time: %.2f seconds\n", (endTime - startTime) / 1000.0);
            System.out.printf("Output directory: %s\n", datasetDir);
            System.out.println("Small dataset generation complete");
            
        } catch (Exception e) {
            System.err.println("Error occurred during small dataset generation: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Main method - can be executed independently
     */
    public static void main(String[] args) {
        try {
            String outputDir = "src/test/resources/test-data";
            
            // Check for specific dataset request
            if (args.length > 0) {
                String requestedDataset = args[0].toLowerCase();
                
                Map<String, Integer> datasetSizes = new LinkedHashMap<>();
                datasetSizes.put("small", 10_000);      // 10K records
                datasetSizes.put("large", 5_000_000);   // 5M records
                datasetSizes.put("xlarge", 10_000_000); // 10M records
                
                if (datasetSizes.containsKey(requestedDataset)) {
                    String datasetName = requestedDataset;
                    int recordCount = datasetSizes.get(requestedDataset);
                    
                    System.out.printf("========================================\n");
                    System.out.printf("Generating %s Dataset (%,d records)\n", 
                        datasetName.toUpperCase(), recordCount);
                    System.out.printf("========================================\n");
                    
                    String datasetDir = outputDir + "/" + datasetName;
                    CsvTestDataGenerator generator = new CsvTestDataGenerator(datasetDir);
                    
                    long startTime = System.currentTimeMillis();
                    generator.generateTestData(recordCount);
                    long endTime = System.currentTimeMillis();
                    
                    System.out.printf("Elapsed time: %.2f seconds\n", (endTime - startTime) / 1000.0);
                    System.out.printf("Output directory: %s\n", datasetDir);
                    System.out.printf("%s dataset generation complete\n", datasetName.toUpperCase());
                    return;
                } else {
                    System.err.printf("Unknown dataset size: %s\n", requestedDataset);
                    System.err.println("Available options: small, large, xlarge");
                    System.exit(1);
                }
            }
            
            // Generate all datasets if no specific request
            Map<String, Integer> datasets = new LinkedHashMap<>();
            datasets.put("small", 10_000);      // 10K records
            datasets.put("large", 5_000_000);   // 5M records
            datasets.put("xlarge", 10_000_000);  // 10M records
            
            for (Map.Entry<String, Integer> dataset : datasets.entrySet()) {
                String datasetName = dataset.getKey();
                int recordCount = dataset.getValue();
                
                System.out.printf("\n========================================\n");
                System.out.printf("Dataset: %s (%,d records)\n", datasetName, recordCount);
                System.out.printf("========================================\n");
                
                String datasetDir = outputDir + "/" + datasetName;
                CsvTestDataGenerator generator = new CsvTestDataGenerator(datasetDir);
                
                long startTime = System.currentTimeMillis();
                generator.generateTestData(recordCount);
                long endTime = System.currentTimeMillis();
                
                System.out.printf("Elapsed time: %.2f seconds\n", (endTime - startTime) / 1000.0);
                System.out.printf("Output directory: %s\n", datasetDir);
            }
            
            System.out.println("\n=== All Dataset Generation Complete ===");
            
        } catch (Exception e) {
            System.err.println("Error occurred during data generation: " + e.getMessage());
            e.printStackTrace();
        }
    }
} 