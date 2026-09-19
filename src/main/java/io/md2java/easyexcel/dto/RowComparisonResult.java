package io.md2java.easyexcel.dto;

import java.util.List;

import lombok.Builder;
import lombok.Data;

/**
 * Result of comparing an associated pair of rows (same unique-key value) between the source
 * and the target file.
 */
@Data
@Builder
public class RowComparisonResult {

    /**
     * Name of the unique-key column used to associate the rows.
     */
    private String keyColumn;

    /**
     * Value of the unique key shared by both rows.
     */
    private String keyValue;

    /**
     * {@link ComparisonStatus#MATCH} when every compared column is equal, otherwise
     * {@link ComparisonStatus#MISMATCH}.
     */
    private ComparisonStatus status;

    /**
     * Per-column comparison results (ignored columns and the key column are not included).
     */
    private List<ColumnComparisonResult> columns;
}