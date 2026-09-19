package io.md2java.easyexcel.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * File- and directory-level settings for the Excel comparison service.
 *
 * <pre>
 * excelcompare.source-directory=./data/source
 * excelcompare.target-directory=./data/target
 * </pre>
 */
@Data
@ConfigurationProperties(prefix = "excelcompare")
public class ExcelCompareProperties {

    /**
     * Directory containing the source (expected) Excel files.
     */
    private String sourceDirectory;

    /**
     * Directory containing the target (generated) Excel files.
     */
    private String targetDirectory;
}