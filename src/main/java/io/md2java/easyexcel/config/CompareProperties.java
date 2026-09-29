package io.md2java.easyexcel.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

@Data
@ConfigurationProperties(prefix = "app.files")
public class CompareProperties {

    private ExcelCompare excelcompare;

    private Map<String, Map<String, SheetConfig>> recon;

    @Data
    public static class ExcelCompare {
        private String sourceDirectory;
        private String targetDirectory;
    }

    @Data
    public static class SheetConfig {
        private List<String> uniqueRowKey;
        private List<String> ignoreColumns;
        private int headerRowIndex;
    }
}
