package io.md2java.easyexcel.dto;

import java.util.Map;

import lombok.Builder;
import lombok.Data;

/**
 * A row that exists in the target file but is not present in the source file
 * (no row with the same unique-key value).
 */
@Data
@Builder
public class ExtraRowResult {

    /**
     * Unique-key value of the extra row.
     */
    private String keyValue;

    /**
     * Full target values of the extra row, keyed by column header.
     */
    private Map<String, String> targetValues;
}