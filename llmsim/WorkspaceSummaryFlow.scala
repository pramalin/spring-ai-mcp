package com.alaiengineering.filesmcp.llmsim

import com.alai.llmsim.{Script, ScriptSource}
import com.alai.llmsim.Script._

/**
 * Deterministic two-turn agent scenario used by the integration test.
 *
 * Turn 1 asks the application to invoke the real MCP workspace_summary tool.
 * Turn 2 renders the actual tool result returned by the application.
 */
object WorkspaceSummaryFlow extends ScriptSource {
  val script: Script = Script.exactly(
    toolCall(
      id = "workspace-summary-1",
      name = "workspace_summary",
      arguments = "{}"
    ),
    replyFromToolResult("workspace-summary-1") { result =>
      s"Workspace inspection completed successfully. MCP tool result: $result"
    }
  )
}
