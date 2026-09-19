package io.md2java.easyexcel.dto;

/**
 * Status of a comparison at file, sheet, row or column level.
 */
public enum ComparisonStatus {

    /**
     * The compared values are equal.
     */
    MATCH,

    /**
     * One or more compared values differ.
     */
    MISMATCH,

    /**
     * The item exists in the source file but is missing from the target file.
     */
    MISSING_IN_TARGET,

    /**
     * The item exists in the target file but is not present in the source file.
     */
    EXTRA_IN_TARGET,

    /**
     * The sheet has no unique-key configuration for this file and was therefore skipped.
     */
    NOT_CONFIGURED,

    /**
     * The expected source/target file could not be found.
     */
    FILE_NOT_FOUND,

    /**
     * An unexpected error occurred while reading or comparing the files.
     */
    ERROR
}