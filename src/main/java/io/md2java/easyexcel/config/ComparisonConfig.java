package io.md2java.easyexcel.config;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Resolves the file- and sheet-specific comparison settings from the application configuration.
 *
 * <p>Supported properties (resolved dynamically from the actual Excel filename and sheet name):
 * <pre>
 * customer.xlsx.Customer.key=CustomerId
 * customer.xlsx.Customer.ignore-columns=CreatedDate,UpdatedDate
 * </pre>
 */
@Component
public class ComparisonConfig {

    private static final String KEY_PROPERTY_SUFFIX = ".key";
    private static final String IGNORE_COLUMNS_PROPERTY_SUFFIX = ".ignore-columns";

    private final Environment environment;

    public ComparisonConfig(Environment environment) {
        this.environment = environment;
    }

    /**
     * Reads {@code <filename>.<sheetName>.key} - the column header used as the unique key to
     * associate rows between the source and the target file.
     */
    public String getKeyColumn(String filename, String sheetName) {
        String value = environment.getProperty(propertyKey(filename, sheetName, KEY_PROPERTY_SUFFIX));
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * Reads {@code <filename>.<sheetName>.ignore-columns} - comma separated list of column headers that
     * must be excluded from the comparison.
     */
    public Set<String> getIgnoredColumns(String filename, String sheetName) {
        String value = environment.getProperty(propertyKey(filename, sheetName, IGNORE_COLUMNS_PROPERTY_SUFFIX));
        if (!StringUtils.hasText(value)) {
            return Collections.emptySet();
        }
        Set<String> ignored = new LinkedHashSet<>();
        for (String column : value.split(",")) {
            if (StringUtils.hasText(column)) {
                ignored.add(column.trim());
            }
        }
        return ignored;
    }

    /**
     * A sheet is considered configured (and therefore comparable) when a unique-key column
     * has been defined for it.
     */
    public boolean isConfigured(String filename, String sheetName) {
        return StringUtils.hasText(getKeyColumn(filename, sheetName));
    }

    private String propertyKey(String filename, String sheetName, String suffix) {
        return filename + "." + sheetName + suffix;
    }
}