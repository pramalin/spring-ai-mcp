package com.alaiengineering.filesmcp.config;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.workspace")
public record WorkspaceProperties(
        Path root,
        int defaultMaxResults,
        int maxResultsLimit,
        int defaultMaxCharacters,
        int maxCharactersLimit,
        long maxReadableFileBytes) {

    public WorkspaceProperties {
        root = root == null ? Path.of("./sample-files") : root;
        defaultMaxResults = positiveOrDefault(defaultMaxResults, 100);
        maxResultsLimit = positiveOrDefault(maxResultsLimit, 500);
        defaultMaxCharacters = positiveOrDefault(defaultMaxCharacters, 12_000);
        maxCharactersLimit = positiveOrDefault(maxCharactersLimit, 50_000);
        maxReadableFileBytes = maxReadableFileBytes > 0 ? maxReadableFileBytes : 2_000_000L;

        if (defaultMaxResults > maxResultsLimit) {
            defaultMaxResults = maxResultsLimit;
        }
        if (defaultMaxCharacters > maxCharactersLimit) {
            defaultMaxCharacters = maxCharactersLimit;
        }
    }

    private static int positiveOrDefault(int value, int defaultValue) {
        return value > 0 ? value : defaultValue;
    }
}
