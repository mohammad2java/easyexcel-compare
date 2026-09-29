package io.md2java.easyexcel.service;

public interface CompareEngine {

    /**
     * Compares only the files/sheets declared under {@code app.files.recon.*}.
     */
    void compare();
}
