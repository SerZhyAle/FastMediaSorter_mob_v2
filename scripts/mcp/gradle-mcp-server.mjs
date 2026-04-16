import { spawn } from "node:child_process";
import { existsSync } from "node:fs";
import { resolve } from "node:path";

const repoRoot = resolve(process.env.FMS_REPO_ROOT || process.cwd());
const gradleWrapper = resolve(repoRoot, "gradlew.bat");

const ALLOWED_TASKS = [
    "assembleStandardDebug",
    "assembleLiteDebug",
    "assemblePhotosDebug",
    "assembleLegacyDebug",
    "assembleStandardRelease",
    "testStandardDebugUnitTest",
    "lintStandardDebug",
    ":wear:assembleDebug",
    ":app_v2:dependencies"
];

const MAX_OUTPUT_CHARS = 120_000;
const EXEC_TIMEOUT_MS = 20 * 60 * 1000;

let inputBuffer = "";

function writeMessage(payload) {
    const body = JSON.stringify(payload);
    process.stdout.write(`Content-Length: ${Buffer.byteLength(body, "utf8")}\r\n\r\n${body}`);
}

function ok(id, result) {
    writeMessage({ jsonrpc: "2.0", id, result });
}

function fail(id, code, message, data) {
    writeMessage({
        jsonrpc: "2.0",
        id,
        error: {
            code,
            message,
            data
        }
    });
}

function clampOutput(text) {
    if (text.length <= MAX_OUTPUT_CHARS) {
        return text;
    }

    const head = text.slice(0, Math.floor(MAX_OUTPUT_CHARS / 2));
    const tail = text.slice(-Math.floor(MAX_OUTPUT_CHARS / 2));
    return `${head}\n\n[output truncated]\n\n${tail}`;
}

function runGradleTask(task) {
    return new Promise((resolvePromise) => {
        if (!existsSync(gradleWrapper)) {
            resolvePromise({
                ok: false,
                exitCode: -1,
                output: `gradlew.bat not found at ${gradleWrapper}`
            });
            return;
        }

        const child = spawn(gradleWrapper, [task], {
            cwd: repoRoot,
            windowsHide: true,
            shell: false
        });

        let output = "";
        const append = (chunk) => {
            output += chunk.toString();
            if (output.length > MAX_OUTPUT_CHARS * 2) {
                output = output.slice(-MAX_OUTPUT_CHARS * 2);
            }
        };

        child.stdout.on("data", append);
        child.stderr.on("data", append);

        const timeout = setTimeout(() => {
            child.kill();
            resolvePromise({
                ok: false,
                exitCode: -1,
                output: `Timed out after ${EXEC_TIMEOUT_MS}ms\n\n${clampOutput(output)}`
            });
        }, EXEC_TIMEOUT_MS);

        child.on("error", (error) => {
            clearTimeout(timeout);
            resolvePromise({
                ok: false,
                exitCode: -1,
                output: `Failed to execute gradlew.bat: ${error.message}`
            });
        });

        child.on("close", (code) => {
            clearTimeout(timeout);
            resolvePromise({
                ok: code === 0,
                exitCode: code ?? -1,
                output: clampOutput(output)
            });
        });
    });
}

async function handleRequest(request) {
    const { id, method, params } = request;

    if (method === "initialize") {
        ok(id, {
            protocolVersion: "2025-03-26",
            capabilities: {
                tools: {}
            },
            serverInfo: {
                name: "fastmediasorter-gradle-safe",
                version: "1.0.0"
            }
        });
        return;
    }

    if (method === "notifications/initialized") {
        return;
    }

    if (method === "tools/list") {
        ok(id, {
            tools: [
                {
                    name: "gradle_list_allowed_tasks",
                    description: "List Gradle tasks allowed by the secure wrapper.",
                    inputSchema: {
                        type: "object",
                        properties: {},
                        additionalProperties: false
                    }
                },
                {
                    name: "gradle_run_task",
                    description: "Run one allowlisted Gradle task via gradlew.bat from repository root.",
                    inputSchema: {
                        type: "object",
                        properties: {
                            task: {
                                type: "string",
                                enum: ALLOWED_TASKS
                            }
                        },
                        required: ["task"],
                        additionalProperties: false
                    }
                }
            ]
        });
        return;
    }

    if (method === "tools/call") {
        const toolName = params?.name;
        const args = params?.arguments || {};

        if (toolName === "gradle_list_allowed_tasks") {
            ok(id, {
                content: [
                    {
                        type: "text",
                        text: JSON.stringify({
                            repoRoot,
                            gradleWrapper,
                            allowedTasks: ALLOWED_TASKS
                        }, null, 2)
                    }
                ]
            });
            return;
        }

        if (toolName === "gradle_run_task") {
            const task = args.task;

            if (typeof task !== "string" || !ALLOWED_TASKS.includes(task)) {
                fail(id, -32602, "Task is not in allowlist", { task, allowedTasks: ALLOWED_TASKS });
                return;
            }

            // Keep a strict task format check to prevent malformed values reaching process spawn.
            if (!/^[:A-Za-z0-9._-]+$/.test(task)) {
                fail(id, -32602, "Task contains unsupported characters", { task });
                return;
            }

            const result = await runGradleTask(task);
            ok(id, {
                content: [
                    {
                        type: "text",
                        text: JSON.stringify(result, null, 2)
                    }
                ],
                isError: !result.ok
            });
            return;
        }

        fail(id, -32601, `Unknown tool: ${toolName}`);
        return;
    }

    fail(id, -32601, `Method not found: ${method}`);
}

function processBuffer() {
    while (true) {
        const headerEnd = inputBuffer.indexOf("\r\n\r\n");
        if (headerEnd === -1) {
            return;
        }

        const headerRaw = inputBuffer.slice(0, headerEnd);
        const headers = headerRaw.split("\r\n");

        let contentLength = -1;
        for (const line of headers) {
            const [name, value] = line.split(":");
            if (name?.toLowerCase() === "content-length") {
                contentLength = Number.parseInt(value?.trim() || "", 10);
            }
        }

        if (!Number.isFinite(contentLength) || contentLength < 0) {
            inputBuffer = "";
            return;
        }

        const bodyStart = headerEnd + 4;
        const bodyEnd = bodyStart + contentLength;
        if (inputBuffer.length < bodyEnd) {
            return;
        }

        const body = inputBuffer.slice(bodyStart, bodyEnd);
        inputBuffer = inputBuffer.slice(bodyEnd);

        let payload;
        try {
            payload = JSON.parse(body);
        } catch {
            continue;
        }

        if (typeof payload?.id === "undefined") {
            continue;
        }

        handleRequest(payload).catch((error) => {
            fail(payload.id, -32603, "Internal error", { message: error?.message || "Unknown error" });
        });
    }
}

process.stdin.setEncoding("utf8");
process.stdin.on("data", (chunk) => {
    inputBuffer += chunk;
    processBuffer();
});
