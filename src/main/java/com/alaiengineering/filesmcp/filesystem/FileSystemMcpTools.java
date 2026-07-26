package com.alaiengineering.filesmcp.filesystem;

import com.alaiengineering.filesmcp.filesystem.WorkspaceFileService.DirectorySummary;
import com.alaiengineering.filesmcp.filesystem.WorkspaceFileService.FileEntry;
import com.alaiengineering.filesmcp.filesystem.WorkspaceFileService.ListResult;
import com.alaiengineering.filesmcp.filesystem.WorkspaceFileService.ReadFileResult;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class FileSystemMcpTools {

    private final WorkspaceFileService files;

    public FileSystemMcpTools(WorkspaceFileService files) {
        this.files = files;
    }

    @McpTool(
            name = "workspace_summary",
            title = "Workspace summary",
            description = "Count all files and directories in the mounted workspace and group files by extension. Use this for questions such as how many files exist.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public DirectorySummary workspaceSummary() {
        return files.summarize(".", true);
    }

    @McpTool(
            name = "count_files",
            title = "Count files",
            description = "Count files and directories under a relative directory in the mounted workspace. Paths must be relative to the workspace root.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public DirectorySummary countFiles(
            @McpToolParam(description = "Relative directory, such as '.' or 'docs'. Defaults to the workspace root.", required = false)
            String directory,
            @McpToolParam(description = "Whether to include all nested subdirectories. Defaults to true.", required = false)
            Boolean recursive) {
        return files.summarize(directory, recursive);
    }

    @McpTool(
            name = "list_files",
            title = "List files",
            description = "List files and directories under a relative workspace directory. Results are bounded to avoid flooding the model context.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public ListResult listFiles(
            @McpToolParam(description = "Relative directory, such as '.' or 'docs'. Defaults to the workspace root.", required = false)
            String directory,
            @McpToolParam(description = "Whether to include nested entries. Defaults to false.", required = false)
            Boolean recursive,
            @McpToolParam(description = "Maximum entries to return. The server applies a safety cap.", required = false)
            Integer maxResults) {
        return files.list(directory, recursive, maxResults);
    }

    @McpTool(
            name = "find_files",
            title = "Find files by name",
            description = "Recursively find workspace files or directories whose name contains a case-insensitive search string.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public ListResult findFiles(
            @McpToolParam(description = "Case-insensitive text that must occur in the file or directory name.", required = true)
            String nameContains,
            @McpToolParam(description = "Relative directory to search. Defaults to the workspace root.", required = false)
            String directory,
            @McpToolParam(description = "Maximum matches to return. The server applies a safety cap.", required = false)
            Integer maxResults) {
        return files.findByName(nameContains, directory, maxResults);
    }

    @McpTool(
            name = "file_metadata",
            title = "File metadata",
            description = "Return type, size, extension, and modification time for one relative workspace path.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public FileEntry fileMetadata(
            @McpToolParam(description = "Relative file or directory path inside the mounted workspace.", required = true)
            String path) {
        return files.metadata(path);
    }

    @McpTool(
            name = "read_text_file",
            title = "Read text file",
            description = "Read a bounded amount of a UTF-8 text file from the mounted workspace. Binary files, oversized files, absolute paths, and paths outside the workspace are rejected.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public ReadFileResult readTextFile(
            @McpToolParam(description = "Relative path of the UTF-8 text file inside the mounted workspace.", required = true)
            String path,
            @McpToolParam(description = "Maximum characters to return. The server applies a safety cap.", required = false)
            Integer maxCharacters) {
        return files.readTextFile(path, maxCharacters);
    }
}
