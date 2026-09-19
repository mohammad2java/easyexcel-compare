package io.md2java.easyexcel.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Result of comparing a single column value between the source and the target row.
 */
@Data
@Builder
public class ColumnComparisonResult {

    /**
     * Header of the compared column.
     */
    private String columnName;

    /**
     * Value read from the source (expected) file.
     */
    private String sourceValue;

    /**
     * Value read from the target (generated) file.
     */
    private String targetValue;

    /**
     * {@link ComparisonStatus#MATCH} when the values are equal, otherwise
     * {@link ComparisonStatus#MISMATCH}.
     */
    private ComparisonStatus status;
}