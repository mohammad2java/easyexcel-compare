package io.md2java.easyexcel.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.alibaba.excel.EasyExcel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import io.md2java.easyexcel.config.ComparisonConfig;
import io.md2java.easyexcel.config.ExcelCompareProperties;
import io.md2java.easyexcel.dto.ColumnComparisonResult;
import io.md2java.easyexcel.dto.ComparisonStatus;
import io.md2java.easyexcel.dto.ExcelCompareResponse;
import io.md2java.easyexcel.dto.MissingRowResult;
import io.md2java.easyexcel.dto.RowComparisonResult;
import io.md2java.easyexcel.dto.SheetComparisonResult;

class ExcelCompareServiceImplTest {

    @TempDir
    Path tempDir;

    private static final List<List<String>> CUSTOMER_HEAD = List.of(
            List.of("CustomerId"), List.of("Name"), List.of("Age"),
            List.of("City"), List.of("UpdatedDate"));

    private ExcelCompareServiceImpl buildService() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("customer.xlsx.Customer.key", "CustomerId");
        properties.put("customer.xlsx.Customer.ignore-columns", "CreatedDate, UpdatedDate");

        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test-properties", properties));

        ExcelCompareProperties compareProperties = new ExcelCompareProperties();
        compareProperties.setSourceDirectory(tempDir.resolve("source").toString());
        compareProperties.setTargetDirectory(tempDir.resolve("target").toString());

        return new ExcelCompareServiceImpl(compareProperties, new ComparisonConfig(environment));
    }

    private void writeCustomer(Path directory, List<List<Object>> rows) {
        directory.toFile().mkdirs();
        File file = directory.resolve("customer.xlsx").toFile();
        EasyExcel.write(file).sheet("Customer").head(CUSTOMER_HEAD).doWrite(rows);
    }

    private void writeCustomerWithSheet(Path directory, String sheetName, List<List<Object>> rows) {
        directory.toFile().mkdirs();
        File file = directory.resolve("customer.xlsx").toFile();
        EasyExcel.write(file).sheet(sheetName).head(CUSTOMER_HEAD).doWrite(rows);
    }

    @Test
    void pairsRowsByKeyAndReportsColumnMismatches() {
        writeCustomer(tempDir.resolve("source"), List.of(
                List.of("1001", "John", 35, "Mumbai", "2024-01-05 07:30:00"),
                List.of("1002", "Jane", 28, "Delhi", "2024-01-06 07:30:00"),
                List.of("1004", "Sam", 41, "Kochi", "2024-01-07 07:30:00")));
        writeCustomer(tempDir.resolve("target"), List.of(
                List.of("1001", "John", 36, "Mumbai", "9999-12-31 23:59:59"),
                List.of("1003", "Bob", 40, "Pune", "2024-02-01 00:00:00")));

        ExcelCompareResponse response = buildService().compareFile("customer.xlsx");

        assertEquals(ComparisonStatus.MISMATCH, response.getStatus());
        assertEquals("customer.xlsx", response.getFilename());
        assertEquals(1, response.getSheets().size());

        SheetComparisonResult sheet = response.getSheets().get(0);
        assertEquals("Customer", sheet.getSheetName());
        assertEquals("CustomerId", sheet.getKeyColumn());
        assertEquals(ComparisonStatus.MISMATCH, sheet.getStatus());
        assertEquals(2, sheet.getIgnoredColumns().size());
        assertEquals(3, sheet.getSourceRowCount());
        assertEquals(2, sheet.getTargetRowCount());

        // Only key 1001 exists in both files, so there is exactly one paired row comparison.
        assertEquals(1, sheet.getRowComparisons().size());
        RowComparisonResult row = sheet.getRowComparisons().get(0);
        assertEquals("1001", row.getKeyValue());
        assertEquals(ComparisonStatus.MISMATCH, row.getStatus());

        Optional<ColumnComparisonResult> age = row.getColumns().stream()
                .filter(c -> "Age".equals(c.getColumnName()))
                .findFirst();
        assertTrue(age.isPresent());
        assertEquals(ComparisonStatus.MISMATCH, age.get().getStatus());
        assertEquals("35", age.get().getSourceValue());
        assertEquals("36", age.get().getTargetValue());

        // The ignored column must never appear in the column comparison.
        assertTrue(row.getColumns().stream().noneMatch(c -> "UpdatedDate".equals(c.getColumnName())));
        // Matching columns are still reported as MATCH.
        assertTrue(row.getColumns().stream()
                .anyMatch(c -> "City".equals(c.getColumnName()) && c.getStatus() == ComparisonStatus.MATCH));

        // Missing rows in target: 1002 and 1004 (in source order).
        assertEquals(List.of("1002", "1004"),
                sheet.getMissingRows().stream().map(MissingRowResult::getKeyValue).toList());

        // Extra rows in target: 1003.
        assertEquals(1, sheet.getExtraRows().size());
        assertEquals("1003", sheet.getExtraRows().get(0).getKeyValue());
    }

    @Test
    void reportsMissingTargetFile() {
        writeCustomer(tempDir.resolve("source"), List.of(List.of("1001", "John", 35, "Mumbai", "x")));

        ExcelCompareResponse response = buildService().compareFile("customer.xlsx");

        assertEquals(ComparisonStatus.FILE_NOT_FOUND, response.getStatus());
        assertEquals(1, response.getMessages().size());
        assertTrue(response.getMessages().get(0).contains("Target file not found"));
        assertTrue(response.getSheets().isEmpty());
    }

    @Test
    void reportsSourceSheetMissingInTarget() {
        writeCustomer(tempDir.resolve("source"), List.of(List.of("1001", "John", 35, "Mumbai", "x")));
        // Target file contains a different sheet name.
        writeCustomerWithSheet(tempDir.resolve("target"), "SomethingElse",
                List.of(List.of("1001", "John", 35, "Mumbai", "x")));

        ExcelCompareResponse response = buildService().compareFile("customer.xlsx");

        assertEquals(ComparisonStatus.MISMATCH, response.getStatus());
        SheetComparisonResult sheet = response.getSheets().get(0);
        assertEquals(ComparisonStatus.MISSING_IN_TARGET, sheet.getStatus());
        assertTrue(sheet.getMessage().contains("missing from the target file"));
    }

    @Test
    void reportsMatchingFilesAsMatch() {
        writeCustomer(tempDir.resolve("source"), List.of(List.of("1001", "John", 35, "Mumbai", "x")));
        writeCustomer(tempDir.resolve("target"), List.of(List.of("1001", "John", 35, "Mumbai", "y")));

        ExcelCompareResponse response = buildService().compareFile("customer.xlsx");

        assertEquals(ComparisonStatus.MATCH, response.getStatus());
        SheetComparisonResult sheet = response.getSheets().get(0);
        assertEquals(ComparisonStatus.MATCH, sheet.getStatus());
        assertEquals(1, sheet.getRowComparisons().size());
        assertEquals(ComparisonStatus.MATCH, sheet.getRowComparisons().get(0).getStatus());
    }

    @Test
    void reportsMatchWhenOnlyKeysAreMissingOrExtra() {
        writeCustomer(tempDir.resolve("source"), List.of(
                List.of("1001", "John", 35, "Mumbai", "x"),
                List.of("1002", "Jane", 28, "Delhi", "x")));
        writeCustomer(tempDir.resolve("target"), List.of(
                List.of("1001", "John", 35, "Mumbai", "x"),
                List.of("1003", "Bob", 40, "Pune", "x")));

        ExcelCompareResponse response = buildService().compareFile("customer.xlsx");

        // Key 1001 exists in both files and every compared cell matches, so the overall status
        // must be MATCH; 1002 (missing) and 1003 (extra) are reported as warnings only.
        assertEquals(ComparisonStatus.MATCH, response.getStatus());
        assertEquals(1, response.getSheets().size());
        SheetComparisonResult sheet = response.getSheets().get(0);
        assertEquals(ComparisonStatus.MATCH, sheet.getStatus());
        assertEquals(1, sheet.getRowComparisons().size());
        assertEquals(ComparisonStatus.MATCH, sheet.getRowComparisons().get(0).getStatus());
        assertEquals(1, sheet.getMissingRows().size());
        assertEquals(1, sheet.getExtraRows().size());
        assertTrue(sheet.getWarnings().stream()
                .anyMatch(warning -> warning.contains("'1002'") && warning.contains("not found in target")));
        assertTrue(sheet.getWarnings().stream()
                .anyMatch(warning -> warning.contains("'1003'") && warning.contains("not present in source")));
    }

    @Test
    void skipsSheetsWithoutKeyConfiguration() {
        writeCustomer(tempDir.resolve("source"), List.of(List.of("1001", "John", 35, "Mumbai", "x")));
        writeCustomerWithSheet(tempDir.resolve("target"), "NotConfigured",
                List.of(List.of("1001", "John", 36, "Mumbai", "x")));

        ExcelCompareResponse response = new ExcelCompareServiceImpl(
                buildProperties(),
                new ComparisonConfig(new StandardEnvironment())).compareFile("customer.xlsx");

        assertEquals(1, response.getSheets().size());
        SheetComparisonResult sheet = response.getSheets().get(0);
        assertEquals("Customer", sheet.getSheetName());
        assertEquals(ComparisonStatus.NOT_CONFIGURED, sheet.getStatus());
    }

    @Test
    void comparesAllFilesInSourceDirectory() {
        writeCustomer(tempDir.resolve("source"), List.of(List.of("1001", "John", 35, "Mumbai", "x")));
        writeCustomer(tempDir.resolve("target"), List.of(List.of("1001", "John", 35, "Mumbai", "x")));

        List<ExcelCompareResponse> responses = buildService().compareAllFiles();

        assertEquals(1, responses.size());
        assertEquals("customer.xlsx", responses.get(0).getFilename());
        assertEquals(ComparisonStatus.MATCH, responses.get(0).getStatus());
    }

    private ExcelCompareProperties buildProperties() {
        ExcelCompareProperties compareProperties = new ExcelCompareProperties();
        compareProperties.setSourceDirectory(tempDir.resolve("source").toString());
        compareProperties.setTargetDirectory(tempDir.resolve("target").toString());
        return compareProperties;
    }

}