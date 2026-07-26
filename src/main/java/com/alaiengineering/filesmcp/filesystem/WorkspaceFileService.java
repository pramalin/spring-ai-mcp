package com.alaiengineering.filesmcp.filesystem;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import com.alaiengineering.filesmcp.config.WorkspaceProperties;

import org.springframework.stereotype.Service;

@Service
public class WorkspaceFileService {

    private final WorkspaceProperties properties;
    private final Path root;

    public WorkspaceFileService(WorkspaceProperties properties) {
        this.properties = properties;
        this.root = initializeRoot(properties.root());
    }

    public Path root() {
        return root;
    }

    public DirectorySummary summarize(String directory, Boolean recursive) {
        Path start = resolveExistingDirectory(directory);
        boolean walkRecursively = recursive == null || recursive;

        long files = 0;
        long directories = 0;
        long symbolicLinks = 0;
        long other = 0;
        long totalBytes = 0;
        Map<String, Long> filesByExtension = new TreeMap<>();

        try (Stream<Path> paths = pathsBelow(start, walkRecursively)) {
            for (Path path : paths.toList()) {
                BasicFileAttributes attributes = attributes(path);
                if (attributes.isRegularFile()) {
                    files++;
                    totalBytes += attributes.size();
                    filesByExtension.merge(extensionOf(path), 1L, Long::sum);
                }
                else if (attributes.isDirectory()) {
                    directories++;
                }
                else if (attributes.isSymbolicLink()) {
                    symbolicLinks++;
                }
                else {
                    other++;
                }
            }
        }
        catch (IOException ex) {
            throw fileSystemError("Could not summarize directory", ex);
        }

        return new DirectorySummary(
                relative(start),
                walkRecursively,
                files,
                directories,
                symbolicLinks,
                other,
                totalBytes,
                filesByExtension);
    }

    public ListResult list(String directory, Boolean recursive, Integer requestedMaxResults) {
        Path start = resolveExistingDirectory(directory);
        boolean walkRecursively = recursive != null && recursive;
        int maxResults = boundedMaxResults(requestedMaxResults);
        List<FileEntry> entries = new ArrayList<>();
        boolean truncated = false;

        try (Stream<Path> paths = pathsBelow(start, walkRecursively)
                .sorted(Comparator.comparing(this::relative))) {
            var iterator = paths.iterator();
            while (iterator.hasNext()) {
                Path path = iterator.next();
                if (entries.size() >= maxResults) {
                    truncated = true;
                    break;
                }
                entries.add(toEntry(path));
            }
        }
        catch (IOException ex) {
            throw fileSystemError("Could not list directory", ex);
        }

        return new ListResult(relative(start), walkRecursively, maxResults, truncated, entries);
    }

    public ListResult findByName(
            String nameContains,
            String directory,
            Integer requestedMaxResults) {

        if (nameContains == null || nameContains.isBlank()) {
            throw new IllegalArgumentException("nameContains must not be blank");
        }

        Path start = resolveExistingDirectory(directory);
        int maxResults = boundedMaxResults(requestedMaxResults);
        String needle = nameContains.toLowerCase(Locale.ROOT);
        List<FileEntry> entries = new ArrayList<>();
        boolean truncated = false;

        try (Stream<Path> paths = Files.walk(start)) {
            var iterator = paths
                    .filter(path -> !path.equals(start))
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).contains(needle))
                    .sorted(Comparator.comparing(this::relative))
                    .iterator();

            while (iterator.hasNext()) {
                Path path = iterator.next();
                if (entries.size() >= maxResults) {
                    truncated = true;
                    break;
                }
                entries.add(toEntry(path));
            }
        }
        catch (IOException ex) {
            throw fileSystemError("Could not search file names", ex);
        }

        return new ListResult(relative(start), true, maxResults, truncated, entries);
    }

    public FileEntry metadata(String path) {
        return toEntry(resolveExisting(path));
    }

    public ReadFileResult readTextFile(String path, Integer requestedMaxCharacters) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("path must not be blank");
        }

        Path file = resolveExisting(path);
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("Path is not a regular file: " + path);
        }

        long size;
        try {
            size = Files.size(file);
        }
        catch (IOException ex) {
            throw fileSystemError("Could not inspect file", ex);
        }

        if (size > properties.maxReadableFileBytes()) {
            throw new IllegalArgumentException(
                    "File is larger than the configured read limit of "
                            + properties.maxReadableFileBytes() + " bytes");
        }

        int maxCharacters = boundedMaxCharacters(requestedMaxCharacters);
        StringBuilder content = new StringBuilder(Math.min(maxCharacters, 8_192));
        boolean truncated = false;

        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            int character;
            while ((character = reader.read()) != -1) {
                if (character == 0) {
                    throw new IllegalArgumentException("File appears to be binary and cannot be read as UTF-8 text");
                }
                if (content.length() >= maxCharacters) {
                    truncated = true;
                    break;
                }
                content.append((char) character);
            }
        }
        catch (MalformedInputException ex) {
            throw new IllegalArgumentException("File is not valid UTF-8 text: " + path, ex);
        }
        catch (IOException ex) {
            throw fileSystemError("Could not read text file", ex);
        }

        return new ReadFileResult(relative(file), size, content.length(), truncated, content.toString());
    }

    private Path initializeRoot(Path configuredRoot) {
        Path normalized = configuredRoot.toAbsolutePath().normalize();
        if (!Files.exists(normalized)) {
            throw new IllegalStateException("Configured workspace directory does not exist: " + normalized);
        }
        if (!Files.isDirectory(normalized)) {
            throw new IllegalStateException("Configured workspace path is not a directory: " + normalized);
        }
        try {
            return normalized.toRealPath();
        }
        catch (IOException ex) {
            throw new IllegalStateException("Could not resolve workspace directory: " + normalized, ex);
        }
    }

    private Path resolveExistingDirectory(String relativePath) {
        Path path = resolveExisting(relativePath);
        if (!Files.isDirectory(path)) {
            throw new IllegalArgumentException("Path is not a directory: " + safeInput(relativePath));
        }
        return path;
    }

    private Path resolveExisting(String relativePath) {
        String input = relativePath == null || relativePath.isBlank() ? "." : relativePath.trim();
        Path requested = Path.of(input);
        if (requested.isAbsolute()) {
            throw new IllegalArgumentException("Absolute paths are not allowed");
        }

        Path normalized = root.resolve(requested).normalize();
        if (!normalized.startsWith(root)) {
            throw new IllegalArgumentException("Path escapes the mounted workspace");
        }

        try {
            Path real = normalized.toRealPath();
            if (!real.startsWith(root)) {
                throw new IllegalArgumentException("Symbolic link escapes the mounted workspace");
            }
            return real;
        }
        catch (IOException ex) {
            throw new IllegalArgumentException("Path does not exist or cannot be accessed: " + input, ex);
        }
    }

    private Stream<Path> pathsBelow(Path directory, boolean recursive) throws IOException {
        Stream<Path> paths = recursive ? Files.walk(directory) : Files.list(directory);
        return paths.filter(path -> !path.equals(directory));
    }

    private FileEntry toEntry(Path path) {
        BasicFileAttributes attributes = attributes(path);
        String type;
        if (attributes.isRegularFile()) {
            type = "file";
        }
        else if (attributes.isDirectory()) {
            type = "directory";
        }
        else if (attributes.isSymbolicLink()) {
            type = "symbolic-link";
        }
        else {
            type = "other";
        }

        return new FileEntry(
                relative(path),
                type,
                attributes.isRegularFile() ? attributes.size() : 0L,
                attributes.lastModifiedTime().toInstant(),
                attributes.isRegularFile() ? extensionOf(path) : "");
    }

    private BasicFileAttributes attributes(Path path) {
        try {
            return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        }
        catch (IOException ex) {
            throw fileSystemError("Could not inspect path " + relative(path), ex);
        }
    }

    private String relative(Path path) {
        Path relative = root.relativize(path);
        String value = relative.toString().replace(path.getFileSystem().getSeparator(), "/");
        return value.isBlank() ? "." : value;
    }

    private String extensionOf(Path path) {
        String name = path.getFileName().toString();
        int separator = name.lastIndexOf('.');
        if (separator <= 0 || separator == name.length() - 1) {
            return "(none)";
        }
        return name.substring(separator + 1).toLowerCase(Locale.ROOT);
    }

    private int boundedMaxResults(Integer requested) {
        int value = requested == null ? properties.defaultMaxResults() : requested;
        if (value <= 0) {
            throw new IllegalArgumentException("maxResults must be greater than zero");
        }
        return Math.min(value, properties.maxResultsLimit());
    }

    private int boundedMaxCharacters(Integer requested) {
        int value = requested == null ? properties.defaultMaxCharacters() : requested;
        if (value <= 0) {
            throw new IllegalArgumentException("maxCharacters must be greater than zero");
        }
        return Math.min(value, properties.maxCharactersLimit());
    }

    private IllegalStateException fileSystemError(String message, IOException cause) {
        return new IllegalStateException(message + ": " + cause.getMessage(), cause);
    }

    private String safeInput(String input) {
        return input == null || input.isBlank() ? "." : input;
    }

    public record DirectorySummary(
            String directory,
            boolean recursive,
            long fileCount,
            long directoryCount,
            long symbolicLinkCount,
            long otherCount,
            long totalFileBytes,
            Map<String, Long> filesByExtension) {
    }

    public record FileEntry(
            String path,
            String type,
            long sizeBytes,
            Instant lastModified,
            String extension) {
    }

    public record ListResult(
            String directory,
            boolean recursive,
            int maxResults,
            boolean truncated,
            List<FileEntry> entries) {
    }

    public record ReadFileResult(
            String path,
            long sizeBytes,
            int charactersReturned,
            boolean truncated,
            String content) {
    }
}
