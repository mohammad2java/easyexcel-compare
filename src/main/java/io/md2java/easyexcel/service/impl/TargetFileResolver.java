package io.md2java.easyexcel.service.impl;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Resolves the target (generated) file that belongs to a source (expected) file.
 *
 * <p>Target files are not required to carry exactly the same name as the source file: the target
 * file name without extension is expected to <em>contain</em> the source file name without
 * extension, for example
 * <pre>
 * source : customer.xlsx
 * target : customer.xlsx
 *          customer_127733_0.xlsx
 *          my_customer_export.xlsx
 * </pre>
 *
 * <p>When several candidates match, the best one is selected deterministically:
 * <ol>
 *     <li>same file name (name + extension),</li>
 *     <li>same extension and the file name starts with the source file name,</li>
 *     <li>same extension and the file name merely contains the source file name,</li>
 *     <li>the same three groups again for candidates with a different extension,</li>
 *     <li>shorter file name first (a plain {@code customer_1.xlsx} wins over
 *     {@code customer_very_long_suffix.xlsx}),</li>
 *     <li>alphabetical order.</li>
 * </ol>
 * The discarded candidates and a name that is not identical to the source file name are reported
 * through {@link Match#getNotes()} so that the caller can expose them to the user. When a file with
 * exactly the source file name exists, it is used and no notes are produced.
 */
final class TargetFileResolver {

    /**
     * Resolves the best target file candidate for the given source file name.
     *
     * @param sourceFilename name of the source file, e.g. {@code customer.xlsx}
     * @param candidates     supported files available in the target directory
     * @return the matched candidate plus notes about the resolution, or an empty match when no
     *         candidate contains the source file name
     */
    Match resolve(String sourceFilename, List<File> candidates) {
        if (sourceFilename == null || sourceFilename.trim().isEmpty() || candidates == null
                || candidates.isEmpty()) {
            return Match.notFound();
        }
        String sourceBaseName = baseName(sourceFilename).toLowerCase(Locale.ROOT);
        if (sourceBaseName.isEmpty()) {
            return Match.notFound();
        }
        String sourceExtension = extension(sourceFilename).toLowerCase(Locale.ROOT);

        List<File> matches = new ArrayList<>();
        for (File candidate : candidates) {
            if (candidate != null && candidate.isFile()
                    && baseName(candidate.getName()).toLowerCase(Locale.ROOT).contains(sourceBaseName)) {
                matches.add(candidate);
            }
        }
        if (matches.isEmpty()) {
            return Match.notFound();
        }
        matches.sort(comparator(sourceBaseName, sourceExtension));

        File best = matches.getFirst();
        List<String> notes = new ArrayList<>();
        if (best.getName().equals(sourceFilename)) {
            // The target file carries exactly the source file name: nothing to report.
            return Match.found(best, notes);
        }
        notes.add("Target file '" + best.getName() + "' was matched to source file '" + sourceFilename
                + "' (the target file name without extension contains the source file name '"
                + baseName(sourceFilename) + "').");
        if (matches.size() > 1) {
            List<String> names = new ArrayList<>();
            for (File file : matches) {
                names.add(file.getName());
            }
            notes.add("Several target files matched source file '" + sourceFilename + "': "
                    + String.join(", ", names) + ". '" + best.getName()
                    + "' was used and the other matches were ignored.");
        }
        return Match.found(best, notes);
    }

    /**
     * File name without its extension, e.g. {@code customer_127733_0} for
     * {@code customer_127733_0.xlsx}.
     */
    static String baseName(String filename) {
        if (filename == null) {
            return "";
        }
        int dotIndex = filename.lastIndexOf('.');
        return dotIndex > 0 ? filename.substring(0, dotIndex) : filename;
    }

    /**
     * Extension of the file name including the leading dot, e.g. {@code .xlsx}; empty when the
     * name has no extension.
     */
    static String extension(String filename) {
        if (filename == null) {
            return "";
        }
        int dotIndex = filename.lastIndexOf('.');
        return dotIndex > 0 ? filename.substring(dotIndex) : "";
    }

    /**
     * Compares two candidates by how closely their name matches the source file name; the smallest
     * value is the best match.
     */
    private static Comparator<File> comparator(String sourceBaseName, String sourceExtension) {
        Comparator<File> byMatchRank = Comparator
                .comparingInt(file -> matchRank(file, sourceBaseName, sourceExtension));
        return byMatchRank
                .thenComparingInt(file -> baseName(file.getName()).length())
                .thenComparing(File::getName, String.CASE_INSENSITIVE_ORDER);
    }

    private static int matchRank(File file, String sourceBaseName, String sourceExtension) {
        String candidateBaseName = baseName(file.getName()).toLowerCase(Locale.ROOT);
        boolean sameExtension = extension(file.getName()).toLowerCase(Locale.ROOT).equals(sourceExtension);
        int rank = sameExtension ? 0 : 3;
        if (candidateBaseName.equals(sourceBaseName)) {
            return rank;
        }
        if (candidateBaseName.startsWith(sourceBaseName)) {
            return rank + 1;
        }
        return rank + 2;
    }

    /**
     * Outcome of a target file resolution: the matched file (if any) and the notes describing how
     * the match was made.
     */
    static final class Match {

        private final File file;
        private final List<String> notes;

        private Match(File file, List<String> notes) {
            this.file = file;
            this.notes = notes;
        }

        static Match found(File file, List<String> notes) {
            return new Match(file, new ArrayList<>(notes));
        }

        static Match notFound() {
            return new Match(null, Collections.emptyList());
        }

        boolean isFound() {
            return file != null;
        }

        File getFile() {
            return file;
        }

        List<String> getNotes() {
            return notes;
        }
    }
}

