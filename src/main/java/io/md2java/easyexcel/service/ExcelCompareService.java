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
     * <p>The target file is resolved by name inside the target directory and does not have to match
     * the source file name exactly: a target file whose name without extension contains the source
     * file name without extension is accepted, e.g. {@code customer_127733_0.xlsx} for
     * {@code customer.xlsx}.
     *
     * @param filename file name of the source file, e.g. {@code customer.xlsx}
     * @return structured comparison result
     */
    ExcelCompareResponse compareFile(String filename);

    /**
     * Compares every Excel file found in the source directory against the matching target file in
     * the target directory (see {@link #compareFile(String)} for how the target file is resolved).
     *
     * @return one structured comparison result per source file
     */
    List<ExcelCompareResponse> compareAllFiles();
}