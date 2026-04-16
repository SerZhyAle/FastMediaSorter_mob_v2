import { spawn } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { z } from "zod";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const workspaceRoot = process.env.WORKSPACE_ROOT
    ? path.resolve(process.env.WORKSPACE_ROOT)
    : path.resolve(__dirname, "..", "..", "..");

const DEFAULT_TIMEOUT_MS = Number(process.env.GRADLE_MCP_TIMEOUT_MS || 15 * 60 * 1000);
const MAX_OUTPUT_CHARS = Number(process.env.GRADLE_MCP_MAX_OUTPUT_CHARS || 40000);

const server = new McpServer({
    name: "fastmediasorter-gradle",
    version: "1.0.0",
});

const BLOCKED_TOKEN_PATTERN = /[;&|`><]/;

function normalizePath(p) {
    return p.replace(/\\/g, "/");
}

function assertUnderWorkspace(absPath) {
    const rel = normalizePath(path.relative(workspaceRoot, absPath));
    if (rel.startsWith("../") || rel === "..") {
        throw new Error("Resolved path escapes workspace root and is not allowed.");
    }
}

function assertSafeToken(token) {
    if (!token || BLOCKED_TOKEN_PATTERN.test(token)) {
        throw new Error(`Unsafe token detected: ${token}`);
    }
}

function stripAnsi(text) {
    return text.replace(/\u001b\[[0-9;]*m/g, "");
}

function normalizeOutput(text) {
    const cleaned = stripAnsi(text)
        .replace(/\r\n/g, "\n")
        .replace(/\r/g, "\n")
        .replace(/\0/g, "")
        .replace(/\n{4,}/g, "\n\n\n")
        .trim();

    if (cleaned.length <= MAX_OUTPUT_CHARS) {
        return {
            text: cleaned,
            truncated: false,
            originalLength: cleaned.length,
        };
    }

    return {
        text: `${cleaned.slice(0, MAX_OUTPUT_CHARS)}\n\n[TRUNCATED: output exceeded ${MAX_OUTPUT_CHARS} chars]`,
        truncated: true,
        originalLength: cleaned.length,
    };
}

function resolveExistingFile(relativePath) {
    const abs = path.resolve(workspaceRoot, relativePath);
    assertUnderWorkspace(abs);
    if (!fs.existsSync(abs)) {
        throw new Error(`Required file not found: ${relativePath}`);
    }
    return abs;
}

const COMMANDS = {
    gradle_assemble_debug: {
        description: "Run gradlew.bat assembleStandardDebug",
        type: "cmd",
        file: "gradlew.bat",
        args: ["assembleStandardDebug"],
    },
    gradle_test_unit: {
        description: "Run gradlew.bat testStandardDebugUnitTest",
        type: "cmd",
        file: "gradlew.bat",
        args: ["testStandardDebugUnitTest"],
    },
    gradle_lint: {
        description: "Run gradlew.bat lintStandardDebug",
        type: "cmd",
        file: "gradlew.bat",
        args: ["lintStandardDebug"],
    },
    gradle_dependencies: {
        description: "Run gradlew.bat :app_v2:dependencies",
        type: "cmd",
        file: "gradlew.bat",
        args: [":app_v2:dependencies"],
    },
    gradle_clean: {
        description: "Run gradlew.bat clean",
        type: "cmd",
        file: "gradlew.bat",
        args: ["clean"],
    },
    script_build_debug: {
        description: "Run build-debug.PS1",
        type: "powershell",
        file: "build-debug.PS1",
        args: [],
    },
    script_build_with_version: {
        description: "Run dev/build-with-version.ps1",
        type: "powershell",
        file: "dev/build-with-version.ps1",
        args: [],
    },
};

function buildProcessSpec(commandKey) {
    const spec = COMMANDS[commandKey];
    if (!spec) {
        throw new Error(`Unsupported command: ${commandKey}`);
    }

    const scriptAbsPath = resolveExistingFile(spec.file);

    assertSafeToken(spec.file);
    for (const arg of spec.args) {
        assertSafeToken(arg);
    }

    if (spec.type === "cmd") {
        return {
            command: "cmd.exe",
            args: ["/d", "/s", "/c", `${spec.file} ${spec.args.join(" ")}`.trim()],
            displayCommand: `${spec.file} ${spec.args.join(" ")}`.trim(),
        };
    }

    if (spec.type === "powershell") {
        return {
            command: "powershell.exe",
            args: [
                "-NoProfile",
                "-ExecutionPolicy",
                "Bypass",
                "-File",
                scriptAbsPath,
                ...spec.args,
            ],
            displayCommand: `${spec.file} ${spec.args.join(" ")}`.trim(),
        };
    }

    throw new Error(`Unknown process type for ${commandKey}`);
}

async function runAllowedCommand(commandKey) {
    const { command, args, displayCommand } = buildProcessSpec(commandKey);

    return new Promise((resolve) => {
        const child = spawn(command, args, {
            cwd: workspaceRoot,
            windowsHide: true,
            env: {
                ...process.env,
            },
        });

        let stdout = "";
        let stderr = "";
        let timedOut = false;

        const timer = setTimeout(() => {
            timedOut = true;
            child.kill("SIGTERM");
        }, DEFAULT_TIMEOUT_MS);

        child.stdout.on("data", (chunk) => {
            stdout += chunk.toString("utf8");
        });

        child.stderr.on("data", (chunk) => {
            stderr += chunk.toString("utf8");
        });

        child.on("close", (code) => {
            clearTimeout(timer);
            const normalizedStdout = normalizeOutput(stdout);
            const normalizedStderr = normalizeOutput(stderr);

            resolve({
                ok: !timedOut && code === 0,
                command: displayCommand,
                exitCode: typeof code === "number" ? code : -1,
                timedOut,
                timeoutMs: DEFAULT_TIMEOUT_MS,
                stdout: normalizedStdout.text,
                stderr: normalizedStderr.text,
                stdoutTruncated: normalizedStdout.truncated,
                stderrTruncated: normalizedStderr.truncated,
                stdoutLength: normalizedStdout.originalLength,
                stderrLength: normalizedStderr.originalLength,
            });
        });
    });
}

function toResultText(result) {
    const lines = [];
    lines.push(`Command: ${result.command}`);
    lines.push(`Exit code: ${result.exitCode}`);
    lines.push(`Timed out: ${result.timedOut ? "yes" : "no"} (timeout ${result.timeoutMs} ms)`);
    lines.push(
        `Stdout: ${result.stdoutLength} chars${result.stdoutTruncated ? " (truncated)" : ""}`
    );
    lines.push(
        `Stderr: ${result.stderrLength} chars${result.stderrTruncated ? " (truncated)" : ""}`
    );

    if (result.stdout) {
        lines.push("\n--- STDOUT ---");
        lines.push(result.stdout);
    }

    if (result.stderr) {
        lines.push("\n--- STDERR ---");
        lines.push(result.stderr);
    }

    return lines.join("\n");
}

function registerNoArgTool(name, title, description, commandKey) {
    server.registerTool(
        name,
        {
            title,
            description,
            inputSchema: {
                dryRun: z.boolean().optional(),
            },
        },
        async ({ dryRun = false }) => {
            if (dryRun) {
                const preview = buildProcessSpec(commandKey);
                return {
                    content: [
                        {
                            type: "text",
                            text: `Dry run: ${preview.displayCommand}`,
                        },
                    ],
                    structuredContent: {
                        dryRun: true,
                        command: preview.displayCommand,
                    },
                };
            }

            const result = await runAllowedCommand(commandKey);
            return {
                content: [{ type: "text", text: toResultText(result) }],
                structuredContent: result,
            };
        }
    );
}

registerNoArgTool(
    "gradle_assemble_debug",
    "Assemble standard debug",
    "Safe allowlisted command: gradlew.bat assembleStandardDebug",
    "gradle_assemble_debug"
);

registerNoArgTool(
    "gradle_test_unit",
    "Run unit tests",
    "Safe allowlisted command: gradlew.bat testStandardDebugUnitTest",
    "gradle_test_unit"
);

registerNoArgTool(
    "gradle_lint",
    "Run lint",
    "Safe allowlisted command: gradlew.bat lintStandardDebug",
    "gradle_lint"
);

registerNoArgTool(
    "gradle_dependencies",
    "Print app dependencies",
    "Safe allowlisted command: gradlew.bat :app_v2:dependencies",
    "gradle_dependencies"
);

registerNoArgTool(
    "gradle_clean",
    "Run clean",
    "Safe allowlisted command: gradlew.bat clean",
    "gradle_clean"
);

registerNoArgTool(
    "script_build_debug",
    "Run build-debug script",
    "Safe allowlisted command: build-debug.PS1",
    "script_build_debug"
);

registerNoArgTool(
    "script_build_with_version",
    "Run versioned build script",
    "Safe allowlisted command: dev/build-with-version.ps1",
    "script_build_with_version"
);

// Pipeline tool uses custom multi-step implementation below.
server.registerTool(
    "gradle_run_pipeline",
    {
        title: "Run verify pipeline",
        description: "Run assemble -> tests -> lint in sequence with fail-fast behavior.",
        inputSchema: {
            stopOnFailure: z.boolean().optional(),
            dryRun: z.boolean().optional(),
        },
    },
    async ({ stopOnFailure = true, dryRun = false }) => {
        const steps = ["gradle_assemble_debug", "gradle_test_unit", "gradle_lint"];

        if (dryRun) {
            const commands = steps.map((key) => buildProcessSpec(key).displayCommand);
            return {
                content: [{ type: "text", text: `Dry run pipeline:\n${commands.join("\n")}` }],
                structuredContent: {
                    dryRun: true,
                    stopOnFailure,
                    commands,
                },
            };
        }

        const results = [];

        for (const step of steps) {
            const result = await runAllowedCommand(step);
            results.push(result);

            if (stopOnFailure && !result.ok) {
                break;
            }
        }

        const pipelineOk = results.every((r) => r.ok);
        const summary = results
            .map((r, i) => `${i + 1}. ${r.command} => exit ${r.exitCode}${r.timedOut ? " (timeout)" : ""}`)
            .join("\n");

        return {
            content: [
                {
                    type: "text",
                    text: `Pipeline ${pipelineOk ? "PASSED" : "FAILED"}\n${summary}`,
                },
            ],
            structuredContent: {
                ok: pipelineOk,
                stopOnFailure,
                results,
            },
        };
    }
);

const transport = new StdioServerTransport();
await server.connect(transport);
