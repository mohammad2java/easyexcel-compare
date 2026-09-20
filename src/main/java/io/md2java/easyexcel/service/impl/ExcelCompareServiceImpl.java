package io.md2java.easyexcel.service.impl;

import java.io.File;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelReader;
import com.alibaba.excel.metadata.data.CellData;
import com.alibaba.excel.read.metadata.ReadSheet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import io.md2java.easyexcel.config.ComparisonConfig;
import io.md2java.easyexcel.config.ExcelCompareProperties;
import io.md2java.easyexcel.dto.ColumnComparisonResult;
import io.md2java.easyexcel.dto.ComparisonStatus;
import io.md2java.easyexcel.dto.ExcelCompareResponse;
import io.md2java.easyexcel.dto.ExtraRowResult;
import io.md2java.easyexcel.dto.MissingRowResult;
import io.md2java.easyexcel.dto.RowComparisonResult;
import io.md2java.easyexcel.dto.SheetComparisonResult;
import io.md2java.easyexcel.service.ExcelCompareService;

/**
 * Default {@link ExcelCompareService} implementation.
 *
 * <p>For every Excel file the source (expected) version is compared against the target
 * (generated) version, sheet by sheet. Rows are associated by the unique-key column that
 * is configured per file and sheet. Columns listed in the {@code ignore-columns} property
 * are excluded from the comparison.
 *
 * <p>The target counterpart of a source file is looked up by name in the target directory: the
 * target file name without extension must contain the source file name without extension, so
 * {@code customer.xlsx} matches {@code customer.xlsx} as well as generated names such as
 * {@code customer_127733_0.xlsx}. Comparison settings are always resolved with the
 * <em>source</em> file name (e.g. {@code customer.xlsx.Sheet1.key}).
 */
@Service
public class ExcelCompareServiceImpl implements ExcelCompareService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExcelCompareServiceImpl.class);

    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(".xlsx", ".xls", ".csv");

    /**
     * Resolves target files whose name is not identical to the source file name
     * (e.g. {@code customer_127733_0.xlsx} for {@code customer.xlsx}).
     */
    private static final TargetFileResolver TARGET_FILE_RESOLVER = new TargetFileResolver();

    private final ExcelCompareProperties properties;
    private final ComparisonConfig config;

    public ExcelCompareServiceImpl(ExcelCompareProperties properties, ComparisonConfig config) {
        this.properties = properties;
        this.config = config;
    }

    @Override
    public ExcelCompareResponse compareFile(String filename) {
        try {
            return doCompareFile(filename);
        } catch (Exception e) {
            LOGGER.error("Failed to compare file '{}'", filename, e);
            return ExcelCompareResponse.builder()
                    .filename(filename)
                    .status(ComparisonStatus.ERROR)
                    .messages(new ArrayList<>(Collections.singletonList("Comparison failed: " + e.getMessage())))
                    .sheets(new ArrayList<>())
                    .build();
        }
    }

    @Override
    public List<ExcelCompareResponse> compareAllFiles() {
        File directory = resolveDirectory(properties.getSourceDirectory(), "source").toFile();
        if (!directory.exists() || !directory.isDirectory()) {
            ExcelCompareResponse error = ExcelCompareResponse.builder()
                    .filename("")
                    .status(ComparisonStatus.FILE_NOT_FOUND)
                    .messages(new ArrayList<>(Collections.singletonList(
                            "Source directory not found: " + directory.getAbsolutePath())))
                    .sheets(new ArrayList<>())
                    .build();
            return Collections.singletonList(error);
        }
        List<ExcelCompareResponse> responses = new ArrayList<>();
        for (File file : listExcelFiles(directory)) {
            responses.add(compareFile(file.getName()));
        }
        return responses;
    }

    private ExcelCompareResponse doCompareFile(String filename) {
        if (isBlank(filename)) {
            return errorResponse(filename, "The filename must not be empty.");
        }

        File sourceDirectory = resolveDirectory(properties.getSourceDirectory(), "source").toFile();
        File targetDirectory = resolveDirectory(properties.getTargetDirectory(), "target").toFile();
        File expectedTargetFile = new File(targetDirectory, filename);

        File sourceFile = new File(sourceDirectory, filename);
        if (!sourceFile.isFile()) {
            return fileNotFoundResponse(filename, sourceFile, expectedTargetFile,
                    "Source file not found: " + sourceFile.getAbsolutePath());
        }
        if (!targetDirectory.isDirectory()) {
            return fileNotFoundResponse(filename, sourceFile, expectedTargetFile,
                    "Target directory not found: " + targetDirectory.getAbsolutePath());
        }

        TargetFileResolver.Match targetMatch = TARGET_FILE_RESOLVER.resolve(filename, listExcelFiles(targetDirectory));
        if (!targetMatch.isFound()) {
            return fileNotFoundResponse(filename, sourceFile, expectedTargetFile,
                    "Target file not found in " + targetDirectory.getAbsolutePath()
                            + ": expected a target file whose name without extension contains '"
                            + TargetFileResolver.baseName(filename) + "'.");
        }

        File targetFile = targetMatch.getFile();
        List<SheetComparisonResult> sheets = compareSheets(sourceFile, targetFile, filename);

        List<String> messages = new ArrayList<>(targetMatch.getNotes());
        ComparisonStatus fileStatus = aggregateStatus(sheets, messages);
        if (fileStatus != ComparisonStatus.MATCH) {
            messages.add(0, "Difference(s) found in file: " + filename);
        }
        return ExcelCompareResponse.builder()
                .filename(filename)
                .sourceFile(sourceFile.getAbsolutePath())
                .targetFile(targetFile.getAbsolutePath())
                .status(fileStatus)
                .messages(messages)
                .sheets(sheets)
                .build();
    }

    /**
     * Response for a comparison that could not be started at all, e.g. because the requested file
     * name is blank.
     */
    private ExcelCompareResponse errorResponse(String filename, String message) {
        return ExcelCompareResponse.builder()
                .filename(filename)
                .sourceFile("")
                .targetFile("")
                .status(ComparisonStatus.ERROR)
                .messages(new ArrayList<>(Collections.singletonList(message)))
                .sheets(new ArrayList<>())
                .build();
    }

    /**
     * Response for a missing source file, target directory or target file. The paths in the response
     * are the expected locations; for the target file the exact-name location is reported because the
     * actual file is resolved by name (see {@link TargetFileResolver}).
     */
    private ExcelCompareResponse fileNotFoundResponse(String filename, File sourceFile, File targetFile,
                                                      String message) {
        return ExcelCompareResponse.builder()
                .filename(filename)
                .sourceFile(absolutePath(sourceFile))
                .targetFile(absolutePath(targetFile))
                .status(ComparisonStatus.FILE_NOT_FOUND)
                .messages(new ArrayList<>(Collections.singletonList(message)))
                .sheets(new ArrayList<>())
                .build();
    }

    private String absolutePath(File file) {
        return file == null ? "" : file.getAbsolutePath();
    }

    private List<SheetComparisonResult> compareSheets(File sourceFile, File targetFile, String filename) {
        List<String> sourceSheets = readSheetNames(sourceFile);
        List<String> targetSheets = readSheetNames(targetFile);

        List<SheetComparisonResult> results = new ArrayList<>();

        for (String sheetName : sourceSheets) {
            if (!config.isConfigured(filename, sheetName)) {
                results.add(SheetComparisonResult.builder()
                        .sheetName(sheetName)
                        .status(ComparisonStatus.NOT_CONFIGURED)
                        .message("No key configuration found (expected property: " + filename + "." + sheetName
                                + ".key). Sheet was skipped.")
                        .rowComparisons(new ArrayList<>())
                        .missingRows(new ArrayList<>())
                        .extraRows(new ArrayList<>())
                        .warnings(new ArrayList<>())
                        .ignoredColumns(new ArrayList<>())
                        .build());
                continue;
            }
            if (!targetSheets.contains(sheetName)) {
                results.add(SheetComparisonResult.builder()
                        .sheetName(sheetName)
                        .status(ComparisonStatus.MISSING_IN_TARGET)
                        .message("Sheet '" + sheetName + "' exists in the source file but is missing from the target file.")
                        .rowComparisons(new ArrayList<>())
                        .missingRows(new ArrayList<>())
                        .extraRows(new ArrayList<>())
                        .warnings(new ArrayList<>())
                        .ignoredColumns(new ArrayList<>())
                        .build());
                continue;
            }
            results.add(compareSheet(sourceFile, targetFile, filename, sheetName));
        }

        // Configured sheets that exist only in the target file
        for (String sheetName : targetSheets) {
            if (sourceSheets.contains(sheetName) || !config.isConfigured(filename, sheetName)) {
                continue;
            }
            results.add(SheetComparisonResult.builder()
                    .sheetName(sheetName)
                    .status(ComparisonStatus.EXTRA_IN_TARGET)
                    .message("Sheet '" + sheetName + "' exists in the target file but is missing from the source file.")
                    .rowComparisons(new ArrayList<>())
                    .missingRows(new ArrayList<>())
                    .extraRows(new ArrayList<>())
                    .warnings(new ArrayList<>())
                    .ignoredColumns(new ArrayList<>())
                    .build());
        }
        return results;
    }

    private SheetComparisonResult compareSheet(File sourceFile, File targetFile, String filename, String sheetName) {
        SheetData sourceData = readSheet(sourceFile, sheetName);
        SheetData targetData = readSheet(targetFile, sheetName);

        String keyColumn = config.getKeyColumn(filename, sheetName);
        Set<String> ignoredColumns = config.getIgnoredColumns(filename, sheetName);

        List<RowComparisonResult> rowResults = new ArrayList<>();
        List<MissingRowResult> missingRows = new ArrayList<>();
        List<ExtraRowResult> extraRows = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        Integer sourceKeyIndex = sourceData.columnIndex(keyColumn);
        Integer targetKeyIndex = targetData.columnIndex(keyColumn);

        if (sourceKeyIndex == null || targetKeyIndex == null) {
            return SheetComparisonResult.builder()
                    .sheetName(sheetName)
                    .keyColumn(keyColumn)
                    .ignoredColumns(new ArrayList<>(ignoredColumns))
                    .status(ComparisonStatus.ERROR)
                    .message("Unique key column '" + keyColumn + "' was not found in the sheet '" + sheetName
                            + "' of one of the files.")
                    .sourceRowCount(sourceData.getRows().size())
                    .targetRowCount(targetData.getRows().size())
                    .rowComparisons(rowResults)
                    .missingRows(missingRows)
                    .extraRows(extraRows)
                    .warnings(warnings)
                    .build();
        }

        Map<String, List<Map<Integer, Object>>> sourceByKey = groupRowsByKey(sourceData, sourceKeyIndex);
        Map<String, List<Map<Integer, Object>>> targetByKey = groupRowsByKey(targetData, targetKeyIndex);

        boolean differencesFound = false;

        for (Map.Entry<String, List<Map<Integer, Object>>> sourceEntry : sourceByKey.entrySet()) {
            String keyValue = sourceEntry.getKey();
            List<Map<Integer, Object>> sourceRows = sourceEntry.getValue();
            List<Map<Integer, Object>> targetRows = targetByKey.get(keyValue);

            if (targetRows == null) {
                for (Map<Integer, Object> sourceRow : sourceRows) {
                    missingRows.add(MissingRowResult.builder()
                            .keyValue(keyValue)
                            .sourceValues(rowToValues(sourceRow, sourceData))
                            .build());
                    warnings.add("Source key '" + keyValue + "' not found in target row. Source row: "
                            + rowToValues(sourceRow, sourceData));
                }
                continue;
            }

            int pairCount = Math.min(sourceRows.size(), targetRows.size());
            for (int i = 0; i < pairCount; i++) {
                RowComparisonResult rowResult = compareRow(sourceData, targetData, keyColumn, keyValue,
                        sourceRows.get(i), targetRows.get(i), ignoredColumns);
                rowResults.add(rowResult);
                if (rowResult.getStatus() != ComparisonStatus.MATCH) {
                    differencesFound = true;
                }
            }
            for (int i = pairCount; i < sourceRows.size(); i++) {
                Map<String, String> sourceValues = rowToValues(sourceRows.get(i), sourceData);
                missingRows.add(MissingRowResult.builder()
                        .keyValue(keyValue)
                        .sourceValues(sourceValues)
                        .build());
                warnings.add("Source key '" + keyValue + "' not found in target row. Source row: "
                        + sourceValues);
            }
            for (int i = pairCount; i < targetRows.size(); i++) {
                extraRows.add(ExtraRowResult.builder()
                        .keyValue(keyValue)
                        .targetValues(rowToValues(targetRows.get(i), targetData))
                        .build());
            }
        }

        for (Map.Entry<String, List<Map<Integer, Object>>> targetEntry : targetByKey.entrySet()) {
            if (sourceByKey.containsKey(targetEntry.getKey())) {
                continue;
            }
            for (Map<Integer, Object> targetRow : targetEntry.getValue()) {
                extraRows.add(ExtraRowResult.builder()
                        .keyValue(targetEntry.getKey())
                        .targetValues(rowToValues(targetRow, targetData))
                        .build());
            }
        }

        return SheetComparisonResult.builder()
                .sheetName(sheetName)
                .keyColumn(keyColumn)
                .ignoredColumns(new ArrayList<>(ignoredColumns))
                .status(differencesFound ? ComparisonStatus.MISMATCH : ComparisonStatus.MATCH)
                .sourceRowCount(sourceData.getRows().size())
                .targetRowCount(targetData.getRows().size())
                .rowComparisons(rowResults)
                .missingRows(missingRows)
                .extraRows(extraRows)
                .warnings(warnings)
                .build();
    }

    private RowComparisonResult compareRow(SheetData sourceData, SheetData targetData, String keyColumn,
                                           String keyValue, Map<Integer, Object> sourceRow,
                                           Map<Integer, Object> targetRow, Set<String> ignoredColumns) {
        List<ColumnComparisonResult> columnResults = new ArrayList<>();
        boolean mismatched = false;

        List<String> columnNames = new ArrayList<>(sourceData.headerNames());
        for (String header : targetData.headerNames()) {
            if (!columnNames.contains(header)) {
                columnNames.add(header);
            }
        }
        for (String columnName : columnNames) {
            if (ignoredColumns.contains(columnName) || keyColumn.equals(columnName)) {
                continue;
            }
            Integer sourceIndex = sourceData.columnIndex(columnName);
            Integer targetIndex = targetData.columnIndex(columnName);

            String sourceValue = sourceIndex == null ? "" : normalizeValue(sourceRow.get(sourceIndex));
            String targetValue = targetIndex == null ? "" : normalizeValue(targetRow.get(targetIndex));

            boolean equal = Objects.equals(sourceValue, targetValue);
            columnResults.add(ColumnComparisonResult.builder()
                    .columnName(columnName)
                    .sourceValue(sourceValue)
                    .targetValue(targetValue)
                    .status(equal ? ComparisonStatus.MATCH : ComparisonStatus.MISMATCH)
                    .build());
            if (!equal) {
                mismatched = true;
            }
        }
        return RowComparisonResult.builder()
                .keyColumn(keyColumn)
                .keyValue(keyValue)
                .status(mismatched ? ComparisonStatus.MISMATCH : ComparisonStatus.MATCH)
                .columns(columnResults)
                .build();
    }

    private Map<String, List<Map<Integer, Object>>> groupRowsByKey(SheetData sheetData, int keyIndex) {
        Map<String, List<Map<Integer, Object>>> grouped = new LinkedHashMap<>();
        for (Map<Integer, Object> row : sheetData.getRows()) {
            String keyValue = normalizeValue(row.get(keyIndex));
            grouped.computeIfAbsent(keyValue, k -> new ArrayList<>()).add(row);
        }
        return grouped;
    }

    private Map<String, String> rowToValues(Map<Integer, Object> row, SheetData sheetData) {
        Map<String, String> values = new LinkedHashMap<>();
        for (Map.Entry<Integer, String> header : sheetData.getHeaders().entrySet()) {
            String headerName = header.getValue();
            if (headerName == null || headerName.trim().isEmpty()) {
                continue;
            }
            values.put(headerName, normalizeValue(row.get(header.getKey())));
        }
        return values;
    }

    // ------------------------------------------------------------------
    // EasyExcel reading
    // ------------------------------------------------------------------

    private List<String> readSheetNames(File file) {
        try (ExcelReader excelReader = EasyExcel.read(file).build()) {
            return excelReader.excelExecutor().sheetList().stream()
                    .map(ReadSheet::getSheetName)
                    .collect(java.util.stream.Collectors.toList());
        }
    }

    private SheetData readSheet(File file, String sheetName) {
        SheetDataListener listener = new SheetDataListener();
        try (ExcelReader excelReader = EasyExcel.read(file).build()) {
            List<ReadSheet> sheets = excelReader.excelExecutor().sheetList();
            ReadSheet sheetToRead = sheets.stream()
                    .filter(sheet -> sheetName.equals(sheet.getSheetName()))
                    .findFirst()
                    .orElse(null);
            if (sheetToRead == null) {
                return SheetData.empty();
            }
            sheetToRead.setCustomReadListenerList(Collections.singletonList(listener));
            excelReader.read(Collections.singletonList(sheetToRead));
        }
        return new SheetData(listener.getHeaders(), listener.getRows());
    }

    private ComparisonStatus aggregateStatus(List<SheetComparisonResult> sheets, List<String> messages) {
        boolean comparedAny = false;
        for (SheetComparisonResult sheet : sheets) {
            ComparisonStatus status = sheet.getStatus();
            if (status == ComparisonStatus.NOT_CONFIGURED) {
                continue;
            }
            comparedAny = true;
            if (status != ComparisonStatus.MATCH) {
                return ComparisonStatus.MISMATCH;
            }
        }
        if (!comparedAny) {
            messages.add("No sheets were compared for this file (no key configuration found).");
            return ComparisonStatus.MISMATCH;
        }
        return ComparisonStatus.MATCH;
    }

    // ------------------------------------------------------------------
    // Value normalization
    // ------------------------------------------------------------------

    /**
     * Normalizes a raw cell value to a canonical string form so that semantically equal
     * values (e.g. {@code 35} vs {@code 35.0}) compare as equal.
     */
    private static String normalizeValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof CellData<?> cellData) {
            return normalizeCellData(cellData);
        }
        if (value instanceof String string) {
            return string.trim();
        }
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal.stripTrailingZeros().toPlainString();
        }
        if (value instanceof BigInteger bigInteger) {
            return bigInteger.toString();
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString()).stripTrailingZeros().toPlainString();
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.format(DATE_TIME_FORMATTER);
        }
        if (value instanceof LocalDate localDate) {
            return localDate.toString();
        }
        if (value instanceof LocalTime localTime) {
            return localTime.toString();
        }
        if (value instanceof Date date) {
            return new SimpleDateFormat(DATE_TIME_PATTERN).format(date);
        }
        if (value instanceof Boolean bool) {
            return bool.toString();
        }
        return String.valueOf(value).trim();
    }

    private static String normalizeCellData(CellData<?> cellData) {
        if (cellData.getStringValue() != null) {
            return cellData.getStringValue().trim();
        }
        if (cellData.getNumberValue() != null) {
            return cellData.getNumberValue().stripTrailingZeros().toPlainString();
        }
        if (cellData.getBooleanValue() != null) {
            return cellData.getBooleanValue().toString();
        }
        if (cellData.getData() != null) {
            return normalizeValue(cellData.getData());
        }
        return "";
    }

    // ------------------------------------------------------------------
    // Directory / file helpers
    // ------------------------------------------------------------------

    private Path resolveDirectory(String configured, String defaultSegment) {
        String value = (configured == null || configured.trim().isEmpty())
                ? "data/" + defaultSegment
                : configured;
        return Path.of(value).toAbsolutePath().normalize();
    }

    private List<File> listExcelFiles(File directory) {
        File[] files = directory.listFiles((dir, name) -> isSupportedExcel(name));
        if (files == null) {
            return Collections.emptyList();
        }
        Arrays.sort(files, Comparator.comparing(File::getName));
        return Arrays.asList(files);
    }

    private boolean isSupportedExcel(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return SUPPORTED_EXTENSIONS.stream().anyMatch(lower::endsWith);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

}