# Gradle MCP (Local, Safe Allowlist)

Local MCP server for FastMediaSorter build operations with strict allowlist and no free-form shell args.

## Security Model

- Fixed command allowlist only.
- Working directory is always workspace root.
- Command timeout is enforced.
- Output is normalized (ANSI stripped, newline normalized).
- Output is truncated to prevent oversized responses.
- Unsafe tokens such as `;`, `&&`, `|`, redirection markers are blocked.

## Tools

- `gradle_assemble_debug` -> `gradlew.bat assembleStandardDebug`
- `gradle_test_unit` -> `gradlew.bat testStandardDebugUnitTest`
- `gradle_lint` -> `gradlew.bat lintStandardDebug`
- `gradle_dependencies` -> `gradlew.bat :app_v2:dependencies`
- `gradle_clean` -> `gradlew.bat clean`
- `script_build_debug` -> `build-debug.PS1`
- `script_build_with_version` -> `dev/build-with-version.ps1`
- `gradle_run_pipeline` -> assemble -> tests -> lint (sequential)

All tools support optional `dryRun: true` to preview command execution.

## Install

```powershell
cd scripts/mcp/gradle-mcp
npm install
```

## Run

```powershell
cd scripts/mcp/gradle-mcp
npm start
```

## Environment Variables

- `WORKSPACE_ROOT` (optional): absolute workspace path.
- `GRADLE_MCP_TIMEOUT_MS` (optional): command timeout, default `900000`.
- `GRADLE_MCP_MAX_OUTPUT_CHARS` (optional): output cap, default `40000`.

## VS Code MCP Config Example

Use `.vscode/mcp.json`:

```json
{
  "servers": {
    "gradle-safe": {
      "type": "stdio",
      "command": "node",
      "args": ["scripts/mcp/gradle-mcp/server.js"],
      "env": {
        "WORKSPACE_ROOT": "${workspaceFolder}",
        "GRADLE_MCP_TIMEOUT_MS": "900000",
        "GRADLE_MCP_MAX_OUTPUT_CHARS": "40000"
      }
    }
  }
}
```
