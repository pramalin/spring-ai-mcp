package com.alaiengineering.filesmcp.filesystem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import com.alaiengineering.filesmcp.config.WorkspaceProperties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceFileServiceTests {

    @TempDir
    Path root;

    private WorkspaceFileService service;

    @BeforeEach
    void setUp() throws Exception {
        Files.createDirectories(root.resolve("docs"));
        Files.writeString(root.resolve("docs/readme.md"), "hello workspace");
        Files.writeString(root.resolve("notes.txt"), "one\ntwo\nthree\n");

        service = new WorkspaceFileService(new WorkspaceProperties(
                root, 100, 500, 12_000, 50_000, 2_000_000));
    }

    @Test
    void summarizesFilesRecursively() {
        var summary = service.summarize(".", true);

        assertThat(summary.fileCount()).isEqualTo(2);
        assertThat(summary.directoryCount()).isEqualTo(1);
        assertThat(summary.filesByExtension()).containsEntry("md", 1L).containsEntry("txt", 1L);
    }

    @Test
    void readsBoundedText() {
        var result = service.readTextFile("notes.txt", 5);

        assertThat(result.content()).isEqualTo("one\nt");
        assertThat(result.truncated()).isTrue();
    }

    @Test
    void rejectsTraversalOutsideWorkspace() {
        assertThatThrownBy(() -> service.metadata("../outside.txt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("escapes");
    }

    @Test
    void findsNamesCaseInsensitively() {
        var result = service.findByName("README", ".", 20);

        assertThat(result.entries()).extracting(WorkspaceFileService.FileEntry::path)
                .containsExactly("docs/readme.md");
    }
}
