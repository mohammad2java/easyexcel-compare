package io.md2java.easyexcel.service.impl;

import com.alibaba.excel.EasyExcel;
import io.md2java.easyexcel.config.CompareProperties;
import io.md2java.easyexcel.excel.SheetData;
import io.md2java.easyexcel.excel.SheetDataListener;
import io.md2java.easyexcel.service.CompareEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Compares only the files/sheets declared under {@code app.files.recon.*}.
 *
 * <p>For every configured entity (e.g. {@code customer}) all Excel files in the source and
 * target directories whose name starts with the entity name are loaded into a single
 * {@code Map<uniqueRowKey, Map<column, value>>} and then compared key by key.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SimpleCompareEngine implements CompareEngine {

    private static final String EXCEL_EXTENSION = ".xlsx";

    private final CompareProperties compareProperties;

    @Override
    public void compare() {
        CompareProperties.ExcelCompare excelCompare = compareProperties.getExcelcompare();
        if (excelCompare == null) {
            log.warn("No 'app.files.excelcompare' configuration found, nothing to compare.");
            return;
        }

        Map<String, Map<String, CompareProperties.SheetConfig>> recon = compareProperties.getRecon();
        if (recon == null || recon.isEmpty()) {
            log.warn("No 'app.files.recon.*' configuration found, nothing to compare.");
            return;
        }

        File sourceDir = new File(excelCompare.getSourceDirectory());
        File targetDir = new File(excelCompare.getTargetDirectory());

        recon.forEach((entity, sheets) -> {
            log.info("==================== Comparing entity '{}' ====================", entity);

            List<File> sourceFiles = findFiles(sourceDir, entity);
            List<File> targetFiles = findFiles(targetDir, entity);

            if (sourceFiles.isEmpty() && targetFiles.isEmpty()) {
                log.warn("No files starting with '{}' found in source or target directory, skipping.", entity);
                return;
            }

            sheets.forEach((sheetName, sheetConfig) -> {
                SheetData sourceRows = load(sourceFiles, sheetName, sheetConfig, "source");
                SheetData targetRows = load(targetFiles, sheetName, sheetConfig, "target");
                compareSheet(entity, sheetName, sourceRows, targetRows);
            });
        });
    }

    private List<File> findFiles(File directory, String entity) {
        List<File> files = new ArrayList<>();
        if (directory == null || !directory.isDirectory()) {
            log.warn("Directory '{}' does not exist.", directory);
            return files;
        }
        File[] candidates = directory.listFiles((dir, name) ->
                name.toLowerCase().startsWith(entity.toLowerCase()) && name.toLowerCase().endsWith(EXCEL_EXTENSION));
        if (candidates != null) {
            for (File file : candidates) {
                files.add(file);
            }
        }
        return files;
    }

    private SheetData load(List<File> files, String sheetName,
                           CompareProperties.SheetConfig sheetConfig, String side) {
        SheetData merged = new SheetData();
        for (File file : files) {
            SheetDataListener listener = new SheetDataListener(sheetName, sheetConfig.getHeaderRowIndex(),
                    sheetConfig.getUniqueRowKey(), sheetConfig.getIgnoreColumns());
            try {
                EasyExcel.read(file, listener)
                        .headRowNumber(sheetConfig.getHeaderRowIndex())
                        .sheet(sheetName)
                        .doRead();
            } catch (Exception ex) {
                log.error("[{}] failed to read sheet '{}' from {} file '{}': {}",
                        sheetName, sheetName, side, file.getName(), ex.getMessage());
                continue;
            }
            SheetData sheetData = listener.getSheetData();
            sheetData.getRows().forEach((key, row) -> {
                if (!merged.containsKey(key)) {
                    merged.put(key, row, sheetData.getRowNumber(key));
                } else {
                    log.warn("[{}] duplicate unique-row-key '{}' across {} files, keeping the first occurrence",
                            sheetName, key, side);
                }
            });
        }
        log.info("[{}] {} side total: {} row(s) from {} file(s)", sheetName, side, merged.size(), files.size());
        return merged;
    }

    private void compareSheet(String entity, String sheetName, SheetData sourceRows, SheetData targetRows) {
        int matched = 0;
        int missingInTarget = 0;
        int missingInSource = 0;
        int different = 0;

        for (Map.Entry<String, Map<String, Object>> entry : sourceRows.getRows().entrySet()) {
            String key = entry.getKey();
            Map<String, Object> sourceRow = entry.getValue();
            Map<String, Object> targetRow = targetRows.getRow(key);

            if (targetRow == null) {
                missingInTarget++;
                log.warn("[{}][{}] MISSING key='{}' -> present in source only, not found in target. source row number: {}",
                        entity, sheetName, key, sourceRows.getRowNumber(key));
                continue;
            }

            matched++;
            Set<String> columns = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            columns.addAll(sourceRow.keySet());
            columns.addAll(targetRow.keySet());

            StringBuilder details = new StringBuilder();
            for (String column : columns) {
                Object sourceValue = valueOf(sourceRow, column);
                Object targetValue = valueOf(targetRow, column);
                if (!equalsValue(sourceValue, targetValue)) {
                    different++;
                    details.append(System.lineSeparator())
                            .append("    mismatch column '").append(column)
                            .append("' [source value='").append(sourceValue)
                            .append("', target value='").append(targetValue).append("']");
                }
            }

            if (details.length() > 0) {
                log.warn("[{}][{}] MISMATCH key='{}' (source row number: {}, target row number: {}){}",
                        entity, sheetName, key, sourceRows.getRowNumber(key), targetRows.getRowNumber(key), details);
            }
        }

        for (Map.Entry<String, Map<String, Object>> entry : targetRows.getRows().entrySet()) {
            String key = entry.getKey();
            if (!sourceRows.containsKey(key)) {
                missingInSource++;
                log.warn("[{}][{}] MISSING key='{}' -> present in target only, not found in source. target row number: {}",
                        entity, sheetName, key, targetRows.getRowNumber(key));
            }
        }

        String status = (different == 0 && missingInTarget == 0 && missingInSource == 0) ? "Matched" : "Mismatched";
        log.info("[{}][{}] status: {} -> matched: {}, different: {}, missing in target: {}, missing in source: {}",
                entity, sheetName, status, matched, different, missingInTarget, missingInSource);
    }

    private Object valueOf(Map<String, Object> row, String column) {
        if (row.containsKey(column)) {
            return row.get(column);
        }
        return row.entrySet().stream()
                .filter(e -> e.getKey().equalsIgnoreCase(column))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private boolean equalsValue(Object left, Object right) {
        if (left == null && right == null) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return String.valueOf(left).trim().equals(String.valueOf(right).trim());
    }
}
