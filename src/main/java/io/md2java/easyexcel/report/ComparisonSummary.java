package io.md2java.easyexcel.report;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Collects the per-sheet {@link SheetSummary} results and logs a consolidated summary once the
 * whole comparison has finished.
 */
@Slf4j
public class ComparisonSummary {

    private final List<SheetSummary> sheets = new ArrayList<>();
    private final List<String> skippedEntities = new ArrayList<>();
    private final List<MissingInSource> missingInSource = new ArrayList<>();

    public void add(SheetSummary sheetSummary) {
        sheets.add(sheetSummary);
    }

    public void addSkipped(String entity) {
        skippedEntities.add(entity);
    }

    public void addMissingInSource(String fileName, String sheetName, List<String> keys) {
        if (keys != null && !keys.isEmpty()) {
            missingInSource.add(new MissingInSource(fileName, sheetName, keys));
        }
    }

    public void logSummary() {
        if (sheets.isEmpty() && skippedEntities.isEmpty()) {
            log.info("No sheets were compared.");
            return;
        }

        logMissingInSource();

        log.info("");
        log.info("==================== Comparison Summary ====================");

        int totalRows = 0;
        int totalMatched = 0;
        int totalMismatched = 0;
        int totalMissingInTarget = 0;

        for (SheetSummary sheet : sheets) {
            totalRows += sheet.getTotalSourceRows();
            totalMatched += sheet.getMatchedRows();
            totalMismatched += sheet.getMismatchedRows();
            totalMissingInTarget += sheet.getMissingInTarget();

            log.info("{}-{} => status: {} | total source rows: {} | matched rows: {} | mismatched rows: {} | "
                            + "mismatch columns: {} | missing rows in destination: {}",
                    sheet.getFileName(), sheet.getSheetName(), sheet.getStatus(), sheet.getTotalSourceRows(),
                    sheet.getMatchedRows(), sheet.getMismatchedRows(), formatFieldCounts(sheet.getMismatchFieldCounts()),
                    sheet.getMissingInTarget());
        }

        for (String entity : skippedEntities) {
            log.info("No files starting with '{}' found in source or target directory, skipping.", entity);
        }

        log.info("Overall - total source rows: {}, matched rows: {}, mismatched rows: {}, missing rows in destination: {}",
                totalRows, totalMatched, totalMismatched, totalMissingInTarget);
        log.info("============================================================");
        log.info("");
    }

    private void logMissingInSource() {
        if (missingInSource.isEmpty()) {
            return;
        }
        log.info("==================== Missing in Source ====================");
        for (MissingInSource missing : missingInSource) {
            log.info("[{}][{}] => count: {} | keys: {}",
                    missing.fileName(), missing.sheetName(), missing.keys().size(), String.join(", ", missing.keys()));
        }
        log.info("===========================================================");
    }

    private String formatFieldCounts(Map<String, Integer> fieldCounts) {
        if (fieldCounts.isEmpty()) {
            return "none";
        }
        StringBuilder builder = new StringBuilder();
        fieldCounts.forEach((field, count) -> {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(field).append('=').append(count);
        });
        return builder.toString();
    }

    private record MissingInSource(String fileName, String sheetName, List<String> keys) {
    }
}
