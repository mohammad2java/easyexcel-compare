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
     * Directory containing the target (generated) Excel files. The name of a generated file only
     * has to contain the source file name: {@code customer.xlsx} may be represented by
     * {@code customer.xlsx} or by {@code customer_127733_0.xlsx}.
     */
    private String targetDirectory;
}