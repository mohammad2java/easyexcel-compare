package io.md2java.easyexcel.service;

import java.util.List;

import io.md2java.easyexcel.dto.ExcelCompareResponse;

/**
 * Service responsible for comparing Excel files in the Source (expected) directory against the
 * Target (generated) directory.
 *
 * <p>The implementation is intentionally hidden behind this interface so that alternative
 * comparison strategies or implementations can be introduced in the future without changing
 * the consumer/controller layer.
 */
public interface ExcelCompareService {

    /**
     * Compares the given Excel file (source vs target) sheet by sheet, using the configured
     * unique-key columns to associate rows. Sheets without a {@code key} configuration are skipped.
     *
     * @param filename file name, e.g. {@code customer.xlsx}
     * @return structured comparison result
     */
    ExcelCompareResponse compareFile(String filename);

    /**
     * Compares every Excel file found in the source directory against the target directory.
     *
     * @return one structured comparison result per matched file
     */
    List<ExcelCompareResponse> compareAllFiles();
}