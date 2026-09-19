package io.md2java.easyexcel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.md2java.easyexcel.config.ExcelCompareProperties;
import io.md2java.easyexcel.dto.ColumnComparisonResult;
import io.md2java.easyexcel.dto.ComparisonStatus;
import io.md2java.easyexcel.dto.ExcelCompareResponse;
import io.md2java.easyexcel.dto.RowComparisonResult;
import io.md2java.easyexcel.dto.SheetComparisonResult;
import io.md2java.easyexcel.service.ExcelCompareService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(ExcelCompareProperties.class)
@Slf4j
@RequiredArgsConstructor
public class EasyexcelcomparatorApplication implements CommandLineRunner {

    private final ExcelCompareService excelCompareService;

    public static void main(String[] args) {
        SpringApplication.run(EasyexcelcomparatorApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("EasyExcel Comparator Application started successfully.");
        log.info("current working directory: " + System.getProperty("user.dir"));
        List<ExcelCompareResponse> results = excelCompareService.compareAllFiles();
        for (ExcelCompareResponse result : results) {
            logComparisonResult(result);
        }
        log.info("EasyExcel Comparator Application finished successfully.");
        log.info(" ");
    }

    private void logComparisonResult(ExcelCompareResponse result) {
        List<String> lines = new ArrayList<>();
        lines.add("== File '" + result.getFilename() + "' compared - status: " + result.getStatus() + " ==");
        for (SheetComparisonResult sheet : safeList(result.getSheets())) {
            appendSheet(lines, sheet);
        }
        for (String message : safeList(result.getMessages())) {
            lines.add("   note: " + message);
        }
        log.info(String.join(System.lineSeparator(), lines));
    }

    private void appendSheet(List<String> lines, SheetComparisonResult sheet) {
        List<RowComparisonResult> rows = safeList(sheet.getRowComparisons());
        long matchedRows = rows.stream()
                .filter(row -> row.getStatus() == ComparisonStatus.MATCH)
                .count();
        long valueMismatchRows = rows.size() - matchedRows;
        List<String> warnings = safeList(sheet.getWarnings());

        lines.add("   Sheet '" + sheet.getSheetName() + "' - status: " + sheet.getStatus());
        lines.add("      rows [source=" + text(sheet.getSourceRowCount())
                + ", target=" + text(sheet.getTargetRowCount()) + "]");
        lines.add("      matched rows   : " + matchedRows);
        lines.add("      mismatched rows: " + valueMismatchRows);
        if (sheet.getMessage() != null && !sheet.getMessage().isEmpty()) {
            lines.add("      note: " + sheet.getMessage());
        }

        for (RowComparisonResult row : rows) {
            if (row.getStatus() == ComparisonStatus.MATCH) {
                continue;
            }
            lines.add("      mismatch - row id '" + row.getKeyValue() + "':");
            for (ColumnComparisonResult column : safeList(row.getColumns())) {
                if (column.getStatus() == ComparisonStatus.MATCH) {
                    continue;
                }
                lines.add("           column '" + column.getColumnName() + "': source='"
                        + column.getSourceValue() + "' target='" + column.getTargetValue() + "'");
            }
        }

        for (String warning : warnings) {
            lines.add("      warning: " + warning);
        }
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? Collections.emptyList() : values;
    }

    private static String text(Object value) {
        return value == null ? "-" : value.toString();
    }
}
