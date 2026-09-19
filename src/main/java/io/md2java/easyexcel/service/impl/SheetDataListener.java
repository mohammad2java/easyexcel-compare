package io.md2java.easyexcel.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.metadata.data.CellData;
import com.alibaba.excel.metadata.data.ReadCellData;
import com.alibaba.excel.read.listener.ReadListener;

/**
 * Collects the header row and the data rows of one sheet into a {@link SheetData}.
 */
final class SheetDataListener implements ReadListener<Map<Integer, Object>> {

    private final Map<Integer, String> headers = new LinkedHashMap<>();
    private final List<Map<Integer, Object>> rows = new ArrayList<>();

    @Override
    public void invokeHead(Map<Integer, ReadCellData<?>> headMap, AnalysisContext context) {
        if (headMap == null) {
            return;
        }
        headMap.forEach((index, cell) -> headers.put(index, cellText(cell)));
    }

    @Override
    public void invoke(Map<Integer, Object> data, AnalysisContext context) {
        if (data == null || data.isEmpty()) {
            return;
        }
        if (data.values().stream().allMatch(this::isBlank)) {
            return;
        }
        rows.add(new LinkedHashMap<>(data));
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        // nothing to release
    }

    Map<Integer, String> getHeaders() {
        return headers;
    }

    List<Map<Integer, Object>> getRows() {
        return rows;
    }

    private String cellText(ReadCellData<?> cell) {
        if (cell == null) {
            return "";
        }
        if (cell.getStringValue() != null) {
            return cell.getStringValue().trim();
        }
        if (cell.getNumberValue() != null) {
            return cell.getNumberValue().stripTrailingZeros().toPlainString();
        }
        if (cell.getBooleanValue() != null) {
            return cell.getBooleanValue().toString();
        }
        if (cell.getData() != null) {
            return String.valueOf(cell.getData());
        }
        return "";
    }

    private boolean isBlank(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof CellData<?> cellData) {
            return cellData.getStringValue() == null
                    && cellData.getNumberValue() == null
                    && cellData.getBooleanValue() == null
                    && cellData.getData() == null;
        }
        return value.toString().trim().isEmpty();
    }
}