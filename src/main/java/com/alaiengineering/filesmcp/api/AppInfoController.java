package com.alaiengineering.filesmcp.api;

import java.util.List;
import java.util.Map;

import com.alaiengineering.filesmcp.filesystem.WorkspaceFileService;
import com.alaiengineering.filesmcp.filesystem.WorkspaceFileService.DirectorySummary;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AppInfoController {

    private final WorkspaceFileService files;

    public AppInfoController(WorkspaceFileService files) {
        this.files = files;
    }

    @GetMapping("/info")
    public Map<String, Object> info() {
        return Map.of(
                "applicationRole", "Read-only filesystem MCP server",
                "mcpTransport", "Streamable HTTP",
                "mcpEndpoint", "/mcp",
                "workspaceRootInContainer", files.root().toString(),
                "readOnly", true,
                "tools", List.of(
                        "workspace_summary",
                        "count_files",
                        "list_files",
                        "find_files",
                        "file_metadata",
                        "read_text_file"));
    }

    @GetMapping("/workspace/summary")
    public DirectorySummary summary(
            @RequestParam(defaultValue = ".") String directory,
            @RequestParam(defaultValue = "true") boolean recursive) {
        return files.summarize(directory, recursive);
    }
}
