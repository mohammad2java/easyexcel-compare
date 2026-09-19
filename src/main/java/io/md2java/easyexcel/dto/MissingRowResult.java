package io.md2java.easyexcel.dto;

import java.util.Map;

import lombok.Builder;
import lombok.Data;

/**
 * A row that exists in the source file but could not be found in the target file
 * (no row with the same unique-key value).
 */
@Data
@Builder
public class MissingRowResult {

    /**
     * Unique-key value of the missing row.
     */
    private String keyValue;

    /**
     * Full source values of the missing row, keyed by column header.
     */
    private Map<String, String> sourceValues;
}