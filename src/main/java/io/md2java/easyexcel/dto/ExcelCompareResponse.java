package io.md2java.easyexcel.dto;

import java.util.List;

import lombok.Builder;
import lombok.Data;

/**
 * Structured result of comparing a single Excel file (source vs target), consumable by
 * a REST API or any other client.
 */
@Data
@Builder
public class ExcelCompareResponse {

    /**
     * Name of the compared Excel file.
     */
    private String filename;

    /**
     * Absolute path of the source file.
     */
    private String sourceFile;

    /**
     * Absolute path of the target file.
     */
    private String targetFile;

    /**
     * Overall result of the file comparison.
     */
    private ComparisonStatus status;

    /**
     * File-level notes (e.g. missing file, skipped comparison).
     */
    private List<String> messages;

    /**
     * Per-sheet comparison results.
     */
    private List<SheetComparisonResult> sheets;
}