package io.md2java.easyexcel.excel;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.alibaba.excel.util.ListUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads a single sheet into a {@link SheetData} keyed by the composite unique-row-key.
 *
 * <p>The header is taken from the configured {@code header-row-index} (1-based) and every
 * following row is keyed by the configured {@code unique-row-key} columns joined with
 * {@code _}. Columns listed in {@code ignore-columns} are dropped from the row map but
 * still contribute to the key. The 1-based Excel row number of every row is retained so
 * differences can be reported against the actual spreadsheet row.</p>
 */
@Slf4j
public class SheetDataListener extends AnalysisEventListener<Map<Integer, String>> {

    private static final String KEY_SEPARATOR = "_";

    private final String sheetName;
    private final int headerRowIndex;
    private final List<String> uniqueRowKey;
    private final List<String> ignoreColumns;
    private final String fileName;

    private final SheetData sheetData = new SheetData();
    private final List<String> headers = new ArrayList<>();

    public SheetDataListener(String sheetName, int headerRowIndex,
                             List<String> uniqueRowKey, List<String> ignoreColumns, String fileName) {
        this.sheetName = sheetName;
        this.headerRowIndex = headerRowIndex;
        this.uniqueRowKey = uniqueRowKey == null ? List.of() : uniqueRowKey;
        this.ignoreColumns = ignoreColumns == null ? List.of() : ignoreColumns;
        this.fileName = fileName;
    }

    @Override
    public void invokeHeadMap(Map<Integer, String> headMap, AnalysisContext context) {
        headers.clear();
        for (Map.Entry<Integer, String> entry : headMap.entrySet()) {
            headers.add(entry.getValue() == null ? "" : entry.getValue().trim());
        }
    }

    @Override
    public void invoke(Map<Integer, String> data, AnalysisContext context) {
        Map<String, Object> rawRow = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String column = headers.get(i);
            if (column.isEmpty()) {
                continue;
            }
            rawRow.put(column, data.get(i));
        }

        Map<String, Object> row = new LinkedHashMap<>();
        rawRow.forEach((column, value) -> {
            if (!isIgnored(column)) {
                row.put(column, value);
            }
        });

        String key = buildKey(rawRow);
        int rowNumber = context.readRowHolder().getRowIndex() + 1;
        if (key.isEmpty()) {
            log.warn("[{}] skipping row {} - unique-row-key {} produced an empty key",
                    sheetName, rowNumber, uniqueRowKey);
            return;
        }
        if (!sheetData.containsKey(key)) {
            sheetData.put(key, row, rowNumber, fileName);
        } else {
            log.warn("[{}] duplicate unique-row-key '{}' found at row {}, keeping the first occurrence",
                    sheetName, key, rowNumber);
        }
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        log.info("[{}] loaded {} row(s) using header-row-index={}", sheetName, sheetData.size(), headerRowIndex);
    }

    private boolean isIgnored(String column) {
        return ignoreColumns.stream().anyMatch(ignored -> ignored.equalsIgnoreCase(column));
    }

    private String buildKey(Map<String, Object> row) {
        List<String> parts = ListUtils.newArrayList();
        for (String keyColumn : uniqueRowKey) {
            Object value = row.get(keyColumn);
            if (value == null) {
                value = row.entrySet().stream()
                        .filter(e -> e.getKey().equalsIgnoreCase(keyColumn))
                        .map(Map.Entry::getValue)
                        .findFirst()
                        .orElse(null);
            }
            parts.add(value == null ? "" : String.valueOf(value).trim());
        }
        return String.join(KEY_SEPARATOR, parts);
    }

    public SheetData getSheetData() {
        return sheetData;
    }

    public List<String> getHeaders() {
        return headers;
    }
}
