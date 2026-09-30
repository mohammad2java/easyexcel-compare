package io.md2java.easyexcel.service.impl;

import com.alibaba.excel.EasyExcel;
import io.md2java.easyexcel.config.CompareProperties;
import io.md2java.easyexcel.excel.SheetData;
import io.md2java.easyexcel.excel.SheetDataListener;
import io.md2java.easyexcel.report.ComparisonSummary;
import io.md2java.easyexcel.report.SheetSummary;
import io.md2java.easyexcel.service.CompareEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Compares only the files/sheets declared under {@code app.files.recon.*}.
 *
 * <p>For every configured entity (e.g. {@code customer}) all Excel files in the source and
 * target directories whose name starts with the entity name are loaded. The comparison is
 * driven by the source data: every source file is compared against the merged target rows and
 * a per file/sheet summary is produced.</p>
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

        ComparisonSummary summary = new ComparisonSummary();

        recon.forEach((entity, sheets) -> {
            log.info("==================== Comparing entity '{}' ====================", entity);

            List<File> sourceFiles = findFiles(sourceDir, entity);
            List<File> targetFiles = findFiles(targetDir, entity);

            if (sourceFiles.isEmpty() && targetFiles.isEmpty()) {
                log.warn("No files starting with '{}' found in source or target directory, skipping.", entity);
                summary.addSkipped(entity);
                return;
            }

            sheets.forEach((sheetName, sheetConfig) -> {
                Map<String, SheetData> targetByFile = loadByFile(targetFiles, sheetName, sheetConfig, "target");
                SheetData targetRows = merge(targetByFile, sheetName, "target");
                SheetData mergedSource = loadMerged(sourceFiles, sheetName, sheetConfig, "source");
                collectMissingInSource(summary, entity, sheetName, targetByFile, mergedSource);
                for (File sourceFile : sourceFiles) {
                    SheetData sourceRows = loadFile(sourceFile, sheetName, sheetConfig, "source");
                    summary.add(compareSheet(entity, sourceFile.getName(), sheetName, sourceRows, targetRows));
                }
            });
        });

        summary.logSummary();
    }

    private List<File> findFiles(File directory, String entity) {
        List<File> files = new ArrayList<>();
        if (directory == null || !directory.isDirectory()) {
            log.warn("Directory '{}' does not exist.", directory);
            return files;
        }
        File[] candidates = directory.listFiles((dir, name) -> matchesEntity(name, entity));
        if (candidates != null) {
            for (File file : candidates) {
                files.add(file);
            }
        }
        return files;
    }

    /**
     * A file belongs to an entity when its name starts with the entity name, is followed only by
     * numeric characters or underscores (e.g. {@code customer_100003_0.xlsx}) and ends with
     * {@code .xlsx}. Names such as {@code customer_payment.xlsx} are therefore excluded.
     */
    private boolean matchesEntity(String fileName, String entity) {
        String lowerName = fileName.toLowerCase();
        String lowerEntity = entity.toLowerCase();
        if (!lowerName.endsWith(EXCEL_EXTENSION)) {
            return false;
        }
        String baseName = lowerName.substring(0, lowerName.length() - EXCEL_EXTENSION.length());
        if (!baseName.startsWith(lowerEntity)) {
            return false;
        }
        String suffix = baseName.substring(lowerEntity.length());
        return suffix.chars().allMatch(ch -> Character.isDigit(ch) || ch == '_');
    }

    private SheetData loadMerged(List<File> files, String sheetName,
                                 CompareProperties.SheetConfig sheetConfig, String side) {
        return merge(loadByFile(files, sheetName, sheetConfig, side), sheetName, side);
    }

    private Map<String, SheetData> loadByFile(List<File> files, String sheetName,
                                              CompareProperties.SheetConfig sheetConfig, String side) {
        Map<String, SheetData> byFile = new LinkedHashMap<>();
        for (File file : files) {
            byFile.put(file.getName(), loadFile(file, sheetName, sheetConfig, side));
        }
        return byFile;
    }

    private SheetData merge(Map<String, SheetData> byFile, String sheetName, String side) {
        SheetData merged = new SheetData();
        byFile.forEach((fileName, sheetData) -> sheetData.getRows().forEach((key, row) -> {
            if (!merged.containsKey(key)) {
                merged.put(key, row, sheetData.getRowNumber(key), sheetData.getFileName(key));
            } else {
                log.warn("[{}] duplicate unique-row-key '{}' across {} files, keeping the first occurrence",
                        sheetName, key, side);
            }
        }));
        log.info("[{}] {} side total: {} row(s) from {} file(s)", sheetName, side, merged.size(), byFile.size());
        return merged;
    }

    private SheetData loadFile(File file, String sheetName,
                               CompareProperties.SheetConfig sheetConfig, String side) {
        SheetDataListener listener = new SheetDataListener(sheetName, sheetConfig.getHeaderRowIndex(),
                sheetConfig.getUniqueRowKey(), sheetConfig.getIgnoreColumns(), file.getName());
        try {
            EasyExcel.read(file, listener)
                    .headRowNumber(sheetConfig.getHeaderRowIndex())
                    .sheet(sheetName)
                    .doRead();
        } catch (Exception ex) {
            log.error("[{}] failed to read sheet '{}' from {} file '{}': {}",
                    sheetName, sheetName, side, file.getName(), ex.getMessage());
        }
        return listener.getSheetData();
    }

    /**
     * Collects, per target file, the keys that do not exist anywhere in the source. These are
     * informational only and do not affect the sheet status, because the comparison is driven by
     * the source data.
     */
    private void collectMissingInSource(ComparisonSummary summary, String entity, String sheetName,
                                        Map<String, SheetData> targetByFile, SheetData sourceRows) {
        targetByFile.forEach((fileName, targetSheetData) -> {
            List<String> missingKeys = new ArrayList<>();
            for (Map.Entry<String, Map<String, Object>> entry : targetSheetData.getRows().entrySet()) {
                String key = entry.getKey();
                if (!sourceRows.containsKey(key)) {
                    missingKeys.add(key);
                    log.info("[{}][{}][{}] MISSING IN SOURCE | key='{}' | target row number: {}",
                            entity, fileName, sheetName, key, targetSheetData.getRowNumber(key));
                }
            }
            summary.addMissingInSource(fileName, sheetName, missingKeys);
        });
    }

    private SheetSummary compareSheet(String entity, String fileName, String sheetName,
                                      SheetData sourceRows, SheetData targetRows) {
        SheetSummary summary = new SheetSummary(entity, fileName, sheetName);
        summary.setTotalSourceRows(sourceRows.size());
        summary.setTotalTargetRows(targetRows.size());

        for (Map.Entry<String, Map<String, Object>> entry : sourceRows.getRows().entrySet()) {
            String key = entry.getKey();
            Map<String, Object> sourceRow = entry.getValue();
            Map<String, Object> targetRow = targetRows.getRow(key);

            if (targetRow == null) {
                summary.incrementMissingInTarget();
                log.warn("[{}][{}][{}] MISSING | key='{}' | source row number: {} | target row number: - | "
                                + "reason: present in source only, not found in target",
                        entity, fileName, sheetName, key, sourceRows.getRowNumber(key));
                continue;
            }

            Set<String> columns = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            columns.addAll(sourceRow.keySet());
            columns.addAll(targetRow.keySet());

            boolean mismatched = false;
            for (String column : columns) {
                Object sourceValue = valueOf(sourceRow, column);
                Object targetValue = valueOf(targetRow, column);
                if (!equalsValue(sourceValue, targetValue)) {
                    mismatched = true;
                    summary.recordMismatchField(column);
                    log.warn("[{}][{}][{}] MISMATCH | key='{}' | column='{}' | source value='{}' | target value='{}' | "
                                    + "source row number: {} | target row number: {}",
                            entity, fileName, sheetName, key, column, sourceValue, targetValue,
                            sourceRows.getRowNumber(key), targetRows.getRowNumber(key));
                }
            }

            if (mismatched) {
                summary.incrementMismatchedRows();
            } else {
                summary.incrementMatchedRows();
            }
        }

        log.info("[{}][{}][{}] status: {} -> total source rows: {}, matched: {}, mismatched: {}, missing in target: {}",
                entity, fileName, sheetName, summary.getStatus(), summary.getTotalSourceRows(), summary.getMatchedRows(),
                summary.getMismatchedRows(), summary.getMissingInTarget());

        return summary;
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
