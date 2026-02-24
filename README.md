# Clojure JVM Multi-Agent System

Conversion of the Python/ClojureScript multi-agent system to Clojure JVM using the Anthropic Java SDK.

## Overview

This project implements a multi-agent research orchestration system with three subagents:

- **docs_researcher**: Finds official documentation
- **repo_analyzer**: Analyzes code repositories
- **web_researcher**: Finds community content

The system uses the Anthropic Java SDK (core API client) with manual agent orchestration, since there is no Java Agent SDK equivalent to Python/TypeScript Agent SDKs.

## Architecture

- `agent.core` - Main entry point, agent loop, interactive CLI
- `agent.config` - Configuration management using Aero
- `agent.tools` - Tool definitions (Skill, Task, Write, Bash, WebSearch, WebFetch, MCP)
- `agent.tool-execution` - Tool execution handlers (stubbed)
- `agent.agents` - Subagent definitions and prompts
- `agent.prompts` - Prompt loading from resources
- `agent.mcp` - MCP server configuration (Notion)
- `agent.utils` - Message display formatting

## Dependencies

See `deps.edn` for full list:

- `com.anthropic/anthropic-java` - Anthropic Java SDK (core API)
- `aero/aero` - Configuration management
- `clj-http/clj-http` - HTTP client for tool implementations
- `cheshire/cheshire` - JSON processing
- `manifold/manifold` - Async utilities
- `com.taoensso/timbre` - Logging

## Configuration

1. Copy `resources/config.edn` to `config.edn` in project root
2. Set environment variables or edit config directly
3. Configuration supports `#env` tags for environment variable substitution

## Usage

### Run the interactive CLI:

```bash
clojure -M:run
```

### In REPL:

```clojure
(require '[agent.core :as core])
(core/-main)
```

## Implementation Status

✅ **Completed:**
- Project structure and dependencies
- Configuration with Aero
- Tool definitions (schemas)
- Basic agent loop skeleton
- Message display formatting
- Prompt loading

🔄 **In Progress:**
- Tool execution implementations
- Subagent orchestration (Task tool)
- MCP server integration
- WebSearch/WebFetch with clj-http

## Key Differences from Python/ClojureScript Versions

1. **No Agent SDK**: Java has no equivalent to Python/TypeScript Agent SDKs
2. **Manual Orchestration**: Subagent coordination must be implemented manually
3. **Java Interop**: Using Anthropic Java SDK via Clojure Java interop
4. **Configuration**: Aero-based config instead of dotenv

## Development Notes

### Tool Execution

Tools are defined in `agent.tools` with JSON schemas. Execution handlers are in `agent.tool-execution` using multimethods. Implement real functionality for:

- `WebSearch`: Use clj-http with search API
- `WebFetch`: Fetch and analyze web pages
- `Bash`: Execute commands with security checks
- `Write`: File operations with permissions
- `Skill`: Invoke external skills
- `Task`: Spawn subagents (complex)

### Subagent Orchestration

The `Task` tool should spawn subagent instances with their own prompts and tools. This requires:

1. Tracking `parent_tool_use_id` in `subagent-registry`
2. Creating separate agent loops for each subagent
3. Routing tool calls to appropriate subagent context
4. Synthesizing results back to main agent

### MCP Integration

Notion MCP server can be started as subprocess. The Java SDK may have MCP client support - needs investigation.

## Testing

Run tests with:

```bash
clojure -M:test
```

## Next Steps

1. Implement proper tool executions
2. Complete subagent orchestration
3. Integrate MCP servers
4. Add error handling and retries
5. Write unit tests
6. Performance optimization

## License

Same as original project.