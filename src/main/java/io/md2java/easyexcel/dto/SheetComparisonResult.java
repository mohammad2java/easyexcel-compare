package io.md2java.easyexcel.dto;

import java.util.List;

import lombok.Builder;
import lombok.Data;

/**
 * Result of the comparison performed for a single sheet of an Excel file.
 */
@Data
@Builder
public class SheetComparisonResult {

    /**
     * Name of the compared sheet.
     */
    private String sheetName;

    /**
     * Unique-key column used to associate rows.
     */
    private String keyColumn;

    /**
     * Columns excluded from the comparison (configured via {@code ...ignore-columns}).
     */
    private List<String> ignoredColumns;

    /**
     * Overall result of the sheet comparison.
     */
    private ComparisonStatus status;

    /**
     * Human readable note about the outcome (e.g. why the sheet was skipped or errored).
     */
    private String message;

    /**
     * Number of data rows read from the source file.
     */
    private Integer sourceRowCount;

    /**
     * Number of data rows read from the target file.
     */
    private Integer targetRowCount;

    /**
     * Per-row comparisons for the rows that could be associated (same unique-key value in both files).
     */
    private List<RowComparisonResult> rowComparisons;

    /**
     * Rows present in the source file but missing from the target file.
     */
    private List<MissingRowResult> missingRows;

    /**
     * Rows present in the target file but not in the source file.
     */
    private List<ExtraRowResult> extraRows;

    /**
     * Non-fatal warnings recorded during the comparison (e.g. rows whose unique-key exists only
     * in one of the files). Warnings never affect {@link #status}.
     */
    private List<String> warnings;
}