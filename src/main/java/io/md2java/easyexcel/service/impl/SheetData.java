package io.md2java.easyexcel.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Light-weight, column-index based model of one Excel sheet read without a DTO/head class.
 */
final class SheetData {

    /**
     * Column index (0-based) to column header text.
     */
    private final Map<Integer, String> headers;

    /**
     * Data rows; each row maps its column index to the raw cell value.
     */
    private final List<Map<Integer, Object>> rows;

    SheetData(Map<Integer, String> headers, List<Map<Integer, Object>> rows) {
        this.headers = new LinkedHashMap<>(headers);
        this.rows = new ArrayList<>(rows);
    }

    static SheetData empty() {
        return new SheetData(Collections.emptyMap(), Collections.emptyList());
    }

    Map<Integer, String> getHeaders() {
        return headers;
    }

    List<Map<Integer, Object>> getRows() {
        return rows;
    }

    /**
     * Returns the column index for the given header, or {@code null} when the column
     * is not present in this sheet.
     */
    Integer columnIndex(String header) {
        if (header == null) {
            return null;
        }
        String trimmed = header.trim();
        for (Map.Entry<Integer, String> entry : headers.entrySet()) {
            if (trimmed.equals(entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Header names ordered by their position in the sheet.
     */
    List<String> headerNames() {
        return headers.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .filter(name -> name != null && !name.trim().isEmpty())
                .collect(Collectors.toList());
    }
}