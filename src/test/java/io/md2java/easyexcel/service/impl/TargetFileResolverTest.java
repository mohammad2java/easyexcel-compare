package io.md2java.easyexcel.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link TargetFileResolver}. The target (generated) file may carry an extra suffix
 * (e.g. {@code customer_127733_0.xlsx} for {@code customer.xlsx}) but always contains the source
 * file name without extension.
 */
class TargetFileResolverTest {

    private final TargetFileResolver resolver = new TargetFileResolver();

    @TempDir
    Path targetDirectory;

    @Test
    void resolvesFileWithIdenticalNameWithoutNotes() throws IOException {
        List<File> candidates = createFiles(targetDirectory, "customer.xlsx", "customer_127733_0.xlsx");

        TargetFileResolver.Match match = resolver.resolve("customer.xlsx", candidates);

        assertThat(match.isFound()).isTrue();
        assertThat(match.getFile().getName()).isEqualTo("customer.xlsx");
        assertThat(match.getNotes()).isEmpty();
    }

    @Test
    void resolvesSuffixedTargetFileWhenExactNameIsMissing() throws IOException {
        List<File> candidates = createFiles(targetDirectory, "customer_127733_0.xlsx");

        TargetFileResolver.Match match = resolver.resolve("customer.xlsx", candidates);

        assertThat(match.isFound()).isTrue();
        assertThat(match.getFile().getName()).isEqualTo("customer_127733_0.xlsx");
        assertThat(match.getNotes()).hasSize(1);
        assertThat(match.getNotes().getFirst())
                .contains("customer_127733_0.xlsx")
                .contains("customer.xlsx");
    }

    @Test
    void resolvesTargetFileWhenSourceNameIsContainedInTheMiddle() throws IOException {
        List<File> candidates = createFiles(targetDirectory, "my_customer_export.xlsx");

        TargetFileResolver.Match match = resolver.resolve("customer.xlsx", candidates);

        assertThat(match.isFound()).isTrue();
        assertThat(match.getFile().getName()).isEqualTo("my_customer_export.xlsx");
    }

    @Test
    void resolvesSourceNameContainingDots() throws IOException {
        List<File> candidates = createFiles(targetDirectory, "customer.report_1.xlsx");

        TargetFileResolver.Match match = resolver.resolve("customer.report.xlsx", candidates);

        assertThat(match.isFound()).isTrue();
        assertThat(match.getFile().getName()).isEqualTo("customer.report_1.xlsx");
    }

    @Test
    void resolvesCaseInsensitiveNames() throws IOException {
        List<File> candidates = createFiles(targetDirectory, "CUSTOMER_Data.xlsx");

        TargetFileResolver.Match match = resolver.resolve("Customer.xlsx", candidates);

        assertThat(match.isFound()).isTrue();
        assertThat(match.getFile().getName()).isEqualTo("CUSTOMER_Data.xlsx");
    }

    @Test
    void ignoresFilesThatDoNotContainTheSourceName() throws IOException {
        List<File> candidates = createFiles(targetDirectory, "invoice_1.xlsx", "order_2.xlsx");

        TargetFileResolver.Match match = resolver.resolve("customer.xlsx", candidates);

        assertThat(match.isFound()).isFalse();
        assertThat(match.getFile()).isNull();
        assertThat(match.getNotes()).isEmpty();
    }

    @Test
    void ignoresDirectories() throws IOException {
        Files.createDirectory(targetDirectory.resolve("customer_folder.xlsx"));
        List<File> candidates = createFiles(targetDirectory, "customer_127733_0.xlsx");

        TargetFileResolver.Match match = resolver.resolve("customer.xlsx", candidates);

        assertThat(match.isFound()).isTrue();
        assertThat(match.getFile().getName()).isEqualTo("customer_127733_0.xlsx");
    }

    @Test
    void prefersCandidateWithSameExtension() throws IOException {
        List<File> candidates = createFiles(targetDirectory, "customer.csv", "customer_127733_0.xlsx");

        TargetFileResolver.Match match = resolver.resolve("customer.xlsx", candidates);

        assertThat(match.getFile().getName()).isEqualTo("customer_127733_0.xlsx");
    }

    @Test
    void prefersPaddedNameOverNameWithSourceNameInTheMiddle() throws IOException {
        List<File> candidates = createFiles(targetDirectory, "my_customer_export.xlsx", "customer_1.xlsx");

        TargetFileResolver.Match match = resolver.resolve("customer.xlsx", candidates);

        assertThat(match.getFile().getName()).isEqualTo("customer_1.xlsx");
    }

    @Test
    void prefersShorterNameAndReportsTheIgnoredMatches() throws IOException {
        List<File> candidates = createFiles(targetDirectory, "customer_export_20240101_very_long.xlsx",
                "customer_127733_0.xlsx");

        TargetFileResolver.Match match = resolver.resolve("customer.xlsx", candidates);

        assertThat(match.getFile().getName()).isEqualTo("customer_127733_0.xlsx");
        assertThat(match.getNotes()).hasSize(2);
        assertThat(match.getNotes().get(1))
                .contains("customer_127733_0.xlsx")
                .contains("customer_export_20240101_very_long.xlsx")
                .contains("customer_127733_0.xlsx' was used");
    }

    @Test
    void reportsAmbiguousMatchesDeterministically() throws IOException {
        List<File> candidates = createFiles(targetDirectory, "customer_2.xlsx", "customer_1.xlsx");

        TargetFileResolver.Match first = resolver.resolve("customer.xlsx", candidates);
        TargetFileResolver.Match second = resolver.resolve("customer.xlsx", candidates);

        assertThat(first.getFile().getName()).isEqualTo("customer_1.xlsx");
        assertThat(second.getFile().getName()).isEqualTo("customer_1.xlsx");
        assertThat(first.getNotes()).hasSize(2);
    }

    @Test
    void returnsEmptyMatchWhenNoCandidatesAreAvailable() {
        assertThat(resolver.resolve("customer.xlsx", List.of()).isFound()).isFalse();
        assertThat(resolver.resolve("customer.xlsx", null).isFound()).isFalse();
        assertThat(resolver.resolve(" ", List.of()).isFound()).isFalse();
    }

    @Test
    void splitsNameAndExtension() {
        assertThat(TargetFileResolver.baseName("customer_127733_0.xlsx")).isEqualTo("customer_127733_0");
        assertThat(TargetFileResolver.extension("customer_127733_0.xlsx")).isEqualTo(".xlsx");
        assertThat(TargetFileResolver.baseName("customer.report.xls")).isEqualTo("customer.report");
        assertThat(TargetFileResolver.extension("customer.report.xls")).isEqualTo(".xls");
        assertThat(TargetFileResolver.baseName("customer")).isEqualTo("customer");
        assertThat(TargetFileResolver.extension("customer")).isEmpty();
        assertThat(TargetFileResolver.baseName(null)).isEmpty();
    }

    private static List<File> createFiles(Path directory, String... names) throws IOException {
        List<File> files = new ArrayList<>();
        for (String name : names) {
            files.add(Files.createFile(directory.resolve(name)).toFile());
        }
        return files;
    }
}
