    
## always use compare_v2 --its working based on configuration only.

how to use -
    ----------------------
    checkout project 
    use build.sh and use start.bat or start.sh to run the project

## sheet selection

Sheets are configured under `app.files.recon.<entity>.<sheet>.*`. The `<sheet>` key can be either
the sheet name or its 1-based position in the workbook:

```properties
# by sheet name
app.files.recon.customer.Sheet1.unique-row-key=uniqueId,Name

# by 1-based sheet index (1 = first sheet, 2 = second sheet, ...)
app.files.recon.customer.1.unique-row-key=uniqueId,Name
```

A purely numeric key is treated as a sheet index; any other value is treated as a sheet name.

## comparison rules

* The comparison is driven by the **source** data only.
* A sheet is `Mismatched` only when a source row is different in the target or is missing from
  the target. Rows that exist in the target but not in the source are logged as `EXTRA` and do
  **not** affect the sheet status.
* A summary is logged at the end of the run, one line per source file / sheet:

```
==================== Comparison Summary ====================
customer.xlsx-Sheet1 | status: Matched | total source rows: 3 | matched rows: 3 | mismatched rows: 0 | mismatch columns: none | missing rows in destination: 0
customer_002.xlsx-Sheet1 | status: Mismatched | total source rows: 3 | matched rows: 2 | mismatched rows: 1 | mismatch columns: age=1 | missing rows in destination: 0
Overall - total source rows: 6, matched rows: 5, mismatched rows: 1, missing rows in destination: 0
============================================================
```


