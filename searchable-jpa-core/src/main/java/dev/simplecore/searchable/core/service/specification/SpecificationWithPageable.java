package dev.simplecore.searchable.core.service.specification;

import lombok.Data;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

/**
 * Holds a JPA Specification and PageRequest together.
 * 
 */
@Data
public class SpecificationWithPageable<T> {
    private final Specification<T> specification;
    private final PageRequest pageRequest;

    SpecificationWithPageable(Specification<T> specification, PageRequest pageRequest) {
        this.specification = specification;
        this.pageRequest = pageRequest;
    }
}