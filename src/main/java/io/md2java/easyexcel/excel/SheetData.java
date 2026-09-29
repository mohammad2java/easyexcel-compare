package io.md2java.easyexcel.excel;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Rows of a single sheet keyed by the composite unique-row-key, together with the
 * 1-based Excel row number each key was read from.
 */
public class SheetData {

    private final Map<String, Map<String, Object>> rows = new LinkedHashMap<>();
    private final Map<String, Integer> rowNumbers = new LinkedHashMap<>();

    public void put(String key, Map<String, Object> row, int rowNumber) {
        rows.put(key, row);
        rowNumbers.put(key, rowNumber);
    }

    public boolean containsKey(String key) {
        return rows.containsKey(key);
    }

    public Map<String, Object> getRow(String key) {
        return rows.get(key);
    }

    public Integer getRowNumber(String key) {
        return rowNumbers.get(key);
    }

    public Map<String, Map<String, Object>> getRows() {
        return rows;
    }

    public Map<String, Integer> getRowNumbers() {
        return rowNumbers;
    }

    public int size() {
        return rows.size();
    }
}
