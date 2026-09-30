package io.md2java.easyexcel.report;

import lombok.Getter;

import java.util.Map;
import java.util.TreeMap;

/**
 * Aggregated comparison result for a single source file / sheet pair.
 *
 * <p>The comparison is driven by the source data, therefore a sheet is only considered
 * {@code Mismatched} when something coming from the source is different in the target or is
 * missing from the target. Rows that exist in the target but not in the source are counted as
 * {@code extraInTarget} and do not affect the sheet status.</p>
 */
@Getter
public class SheetSummary {

    private final String entity;
    private final String fileName;
    private final String sheetName;

    private int totalSourceRows;
    private int totalTargetRows;
    private int matchedRows;
    private int mismatchedRows;
    private int missingInTarget;

    /** Distinct mismatched column name -> number of rows in which it mismatched. */
    private final Map<String, Integer> mismatchFieldCounts = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    public SheetSummary(String entity, String fileName, String sheetName) {
        this.entity = entity;
        this.fileName = fileName;
        this.sheetName = sheetName;
    }

    public void setTotalSourceRows(int totalSourceRows) {
        this.totalSourceRows = totalSourceRows;
    }

    public void setTotalTargetRows(int totalTargetRows) {
        this.totalTargetRows = totalTargetRows;
    }

    public void incrementMatchedRows() {
        matchedRows++;
    }

    public void incrementMismatchedRows() {
        mismatchedRows++;
    }

    public void incrementMissingInTarget() {
        missingInTarget++;
    }

    public void recordMismatchField(String column) {
        mismatchFieldCounts.merge(column, 1, Integer::sum);
    }

    /**
     * A sheet is {@code Mismatched} only when a source row is different in the target or is
     * missing from the target. Rows that exist in the target but not in the source are ignored
     * for the status.
     */
    public String getStatus() {
        return (mismatchedRows == 0 && missingInTarget == 0) ? "Matched" : "Mismatched";
    }
}
