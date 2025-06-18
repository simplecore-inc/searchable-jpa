package dev.simplecore.searchable.core.service.cursor;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Converts between OFFSET-based PageRequest and cursor-based pagination.
 * Maintains API compatibility while using cursor-based queries internally.
 *
 * @param <T> The entity type
 */
@Slf4j
public class CursorPageConverter<T> {

    private final CursorCalculator<T> cursorCalculator;
    private final CursorSpecificationBuilder<T> cursorSpecificationBuilder;
    private final JpaSpecificationExecutor<T> specificationExecutor;

    public CursorPageConverter(@NonNull JpaSpecificationExecutor<T> specificationExecutor,
                              @NonNull Class<T> entityClass) {
        this.specificationExecutor = specificationExecutor;
        this.cursorCalculator = new CursorCalculator<>(specificationExecutor, entityClass);
        this.cursorSpecificationBuilder = new CursorSpecificationBuilder<>();
    }

    /**
     * Converts OFFSET-based PageRequest to cursor-based query execution.
     * ALL pages now use cursor-based approach for consistent performance.
     * 
     * @param originalPageRequest the original OFFSET-based page request
     * @param baseSpecification the base specification for filtering
     * @return Page object with original pagination metadata
     */
    public Page<T> convertToCursorBasedPage(@NonNull PageRequest originalPageRequest,
                                          Specification<T> baseSpecification) {
        try {
            // For first page, execute directly without cursor conditions
            if (originalPageRequest.getPageNumber() == 0) {
                return executeFirstPageWithCursorStructure(originalPageRequest, baseSpecification);
            }

            // Calculate cursor values for the target page
            Map<String, Object> cursorValues = calculateCursorValues(originalPageRequest, baseSpecification);
            
            if (cursorValues.isEmpty()) {
                // If cursor values are empty for non-first page, it means we're beyond available data
                log.debug("No cursor values found for page {}, returning empty page", originalPageRequest.getPageNumber());
                long totalElements = calculateTotalElements(baseSpecification);
                return new PageImpl<>(Collections.emptyList(), originalPageRequest, totalElements);
            }

            // Execute cursor-based query
            Page<T> cursorPage = executeCursorQuery(originalPageRequest, baseSpecification, cursorValues);
            
            // Convert back to original page format
            Page<T> result = convertToOriginalPageFormat(cursorPage, originalPageRequest, baseSpecification);
            

            
            return result;

        } catch (Exception e) {
            log.error("Cursor-based pagination completely failed: {}", e.getMessage(), e);
            // Last resort: return empty page with correct metadata
            return new PageImpl<>(Collections.emptyList(), originalPageRequest, 0);
        }
    }

    /**
     * Executes first page query using cursor structure but without cursor conditions.
     * This ensures consistent behavior across all pages.
     */
    private Page<T> executeFirstPageWithCursorStructure(PageRequest pageRequest, Specification<T> baseSpecification) {
        // Request one extra record to determine if there's a next page
        PageRequest cursorPageRequest = PageRequest.of(
            0, 
            pageRequest.getPageSize() + 1, // +1 to check hasNext
            pageRequest.getSort()
        );

        Page<T> cursorPage = specificationExecutor.findAll(baseSpecification, cursorPageRequest);
        
        // Convert to original page format
        return convertToOriginalPageFormat(cursorPage, pageRequest, baseSpecification);
    }



    /**
     * Calculates cursor values for the target page.
     */
    private Map<String, Object> calculateCursorValues(PageRequest pageRequest, Specification<T> baseSpecification) {
        // Calculate the offset of the last record of the previous page
        int targetOffset = pageRequest.getPageNumber() * pageRequest.getPageSize() - 1;
        
        return cursorCalculator.calculateCursorValues(targetOffset, baseSpecification, pageRequest.getSort());
    }

    /**
     * Executes cursor-based query.
     */
    private Page<T> executeCursorQuery(PageRequest originalPageRequest, 
                                     Specification<T> baseSpecification,
                                     Map<String, Object> cursorValues) {
        
        // Build cursor specification
        List<Sort.Order> sortOrders = originalPageRequest.getSort().toList();
        Specification<T> cursorSpec = cursorSpecificationBuilder.buildCursorSpecification(
            cursorValues, sortOrders, true // forward pagination
        );

        // Combine base specification with cursor specification
        Specification<T> combinedSpec = baseSpecification != null ? 
            baseSpecification.and(cursorSpec) : cursorSpec;

        // Create cursor-based page request (always start from page 0)
        // Request one extra record to determine if there's a next page
        PageRequest cursorPageRequest = PageRequest.of(
            0, 
            originalPageRequest.getPageSize() + 1, // +1 to check hasNext
            originalPageRequest.getSort()
        );

        return specificationExecutor.findAll(combinedSpec, cursorPageRequest);
    }

    /**
     * Converts cursor query result back to original page format.
     */
    private Page<T> convertToOriginalPageFormat(Page<T> cursorPage, 
                                              PageRequest originalPageRequest,
                                              Specification<T> baseSpecification) {
        
        List<T> content = cursorPage.getContent();
        int requestedSize = originalPageRequest.getPageSize();
        
        // Determine if there are more records
        boolean hasNext = content.size() > requestedSize;
        if (hasNext) {
            // Remove the extra record we requested
            content = content.subList(0, requestedSize);
        }

        // Calculate total elements (expensive operation - consider caching)
        long totalElements = calculateTotalElements(baseSpecification);
        
        // Calculate page metadata
        PageMetadata metadata = calculatePageMetadata(
            originalPageRequest, content.size(), totalElements, hasNext
        );

        return new PageImpl<>(content, originalPageRequest, totalElements);
    }

    /**
     * Calculates total elements count.
     * This is an expensive operation and should be optimized or cached.
     */
    private long calculateTotalElements(Specification<T> baseSpecification) {
        try {
            return specificationExecutor.count(baseSpecification);
        } catch (Exception e) {
            log.warn("Failed to calculate total elements: {}", e.getMessage());
            return 0; // Return 0 if count fails
        }
    }

    /**
     * Calculates page metadata for the original page format.
     */
    private PageMetadata calculatePageMetadata(PageRequest originalPageRequest,
                                             int contentSize,
                                             long totalElements,
                                             boolean hasNext) {
        
        int pageNumber = originalPageRequest.getPageNumber();
        int pageSize = originalPageRequest.getPageSize();
        
        int totalPages = totalElements > 0 ? (int) Math.ceil((double) totalElements / pageSize) : 0;
        boolean hasPrevious = pageNumber > 0;
        boolean isFirst = pageNumber == 0;
        boolean isLast = pageNumber >= totalPages - 1;
        
        return new PageMetadata(
            pageNumber, pageSize, totalElements, totalPages,
            hasNext, hasPrevious, isFirst, isLast, contentSize
        );
    }

    /**
     * Metadata holder for page information.
     */
    public static class PageMetadata {
        public final int pageNumber;
        public final int pageSize;
        public final long totalElements;
        public final int totalPages;
        public final boolean hasNext;
        public final boolean hasPrevious;
        public final boolean isFirst;
        public final boolean isLast;
        public final int contentSize;

        public PageMetadata(int pageNumber, int pageSize, long totalElements, int totalPages,
                          boolean hasNext, boolean hasPrevious, boolean isFirst, boolean isLast,
                          int contentSize) {
            this.pageNumber = pageNumber;
            this.pageSize = pageSize;
            this.totalElements = totalElements;
            this.totalPages = totalPages;
            this.hasNext = hasNext;
            this.hasPrevious = hasPrevious;
            this.isFirst = isFirst;
            this.isLast = isLast;
            this.contentSize = contentSize;
        }
    }

    /**
     * Optimized version for cases where total count is not needed.
     * Returns a page with estimated metadata to avoid expensive count queries.
     * ALL pages use cursor-based approach.
     */
    public Page<T> convertToCursorBasedPageWithoutCount(@NonNull PageRequest originalPageRequest,
                                                       Specification<T> baseSpecification) {
        try {
            // For first page, execute with cursor structure
            if (originalPageRequest.getPageNumber() == 0) {
                return executeFirstPageWithoutCount(originalPageRequest, baseSpecification);
            }

            // Calculate cursor values
            Map<String, Object> cursorValues = calculateCursorValues(originalPageRequest, baseSpecification);
            
            if (cursorValues.isEmpty()) {
                // If cursor values are empty for non-first page, it means we're beyond available data
                log.debug("No cursor values found for page {}, returning empty page", originalPageRequest.getPageNumber());
                return new PageImpl<>(Collections.emptyList(), originalPageRequest, -1);
            }

            // Execute cursor-based query
            Page<T> cursorPage = executeCursorQuery(originalPageRequest, baseSpecification, cursorValues);
            
            List<T> content = cursorPage.getContent();
            int requestedSize = originalPageRequest.getPageSize();
            
            boolean hasNext = content.size() > requestedSize;
            if (hasNext) {
                content = content.subList(0, requestedSize);
            }

            // Return page without total count (set to -1 to indicate unknown)
            return new PageImpl<>(content, originalPageRequest, -1);

        } catch (Exception e) {
            log.error("Cursor-based pagination completely failed: {}", e.getMessage(), e);
            // Return empty page instead of falling back to offset
            return new PageImpl<>(Collections.emptyList(), originalPageRequest, -1);
        }
    }

    /**
     * Executes first page without total count calculation.
     */
    private Page<T> executeFirstPageWithoutCount(PageRequest pageRequest, Specification<T> baseSpecification) {
        // Request one extra record to determine if there's a next page
        PageRequest cursorPageRequest = PageRequest.of(
            0, 
            pageRequest.getPageSize() + 1, // +1 to check hasNext
            pageRequest.getSort()
        );

        Page<T> cursorPage = specificationExecutor.findAll(baseSpecification, cursorPageRequest);
        
        List<T> content = cursorPage.getContent();
        int requestedSize = pageRequest.getPageSize();
        
        boolean hasNext = content.size() > requestedSize;
        if (hasNext) {
            content = content.subList(0, requestedSize);
        }

        // Return page without total count
        return new PageImpl<>(content, pageRequest, -1);
    }
} 