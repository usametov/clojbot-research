(ns agent.tools
  "Tool definitions for the multi-agent system.
   Defines schemas for Skill, Task, Write, Bash, WebSearch, WebFetch, and MCP tools."
  (:import [com.anthropic.models.messages Tool]
           [com.anthropic.core JsonValue]
           [java.util Map List]))

(defn json-value
  "Create a JsonValue from a Clojure value."
  [value]
  (JsonValue/from value))

(defn create-tool
  "Create a Tool instance with given name, description, and input schema."
  [name description input-schema-map]
  (let [builder (Tool/builder)]
    (.name builder name)
    (.description builder description)
    (.inputSchema builder (json-value input-schema-map))
    (.build builder)))

(def skill-tool-schema
  "Schema for Skill tool."
  {:type "object"
   :properties {"skill" {:type "string" :description "The skill name to invoke"}
                "args" {:type "string" :description "Arguments for the skill"}}
   :required ["skill"]})

(def task-tool-schema
  "Schema for Task tool (subagent spawning)."
  {:type "object"
   :properties {"subagent_type" {:type "string" :description "Type of subagent to spawn"}
                "description" {:type "string" :description "Task description for the subagent"}
                "prompt" {:type "string" :description "Optional prompt override"}}
   :required ["subagent_type" "description"]})

(def write-tool-schema
  "Schema for Write tool (file creation)."
  {:type "object"
   :properties {"file_path" {:type "string" :description "Path to file to write"}
                "content" {:type "string" :description "Content to write to file"}}
   :required ["file_path" "content"]})

(def bash-tool-schema
  "Schema for Bash tool (command execution)."
  {:type "object"
   :properties {"command" {:type "string" :description "Bash command to execute"}
                "working_directory" {:type "string" :description "Working directory for command"}}
   :required ["command"]})

(def web-search-tool-schema
  "Schema for WebSearch tool."
  {:type "object"
   :properties {"query" {:type "string" :description "Search query"}
                "max_results" {:type "integer" :description "Maximum number of results"}}
   :required ["query"]})

(def web-fetch-tool-schema
  "Schema for WebFetch tool."
  {:type "object"
   :properties {"url" {:type "string" :description "URL to fetch"}
                "prompt" {:type "string" :description "Prompt for analysis"}}
   :required ["url"]})

(defn skill-tool [] (create-tool "Skill" "Invoke a skill" skill-tool-schema))
(defn task-tool [] (create-tool "Task" "Spawn a subagent" task-tool-schema))
(defn write-tool [] (create-tool "Write" "Write to a file" write-tool-schema))
(defn bash-tool [] (create-tool "Bash" "Execute a bash command" bash-tool-schema))
(defn web-search-tool [] (create-tool "WebSearch" "Search the web" web-search-tool-schema))
(defn web-fetch-tool [] (create-tool "WebFetch" "Fetch and analyze web content" web-fetch-tool-schema))

(defn mcp-tool
  "Create an MCP tool with given full name (e.g., 'mcp__notion__API-post-search').
   MCP tools typically have dynamic schemas; we use a generic schema for now."
  [tool-name]
  (create-tool tool-name "MCP server tool" {:type "object" :properties {} :required []}))

(defn default-tools
  "Return list of default tools (excluding MCP tools)."
  []
  [(skill-tool) (task-tool) (write-tool) (bash-tool) (web-search-tool) (web-fetch-tool)])

(defn mcp-tools
  "Return list of MCP tools based on configuration."
  []
  (map mcp-tool (agent.mcp/mcp-tool-names)))

(defn all-tools
  "Return all tools for the system."
  []
  (concat (default-tools) (mcp-tools)))

;; Tool registry and utilities

(def tool-registry
  "Map of tool names to tool constructor functions."
  {"Skill" skill-tool
   "Task" task-tool
   "Write" write-tool
   "Bash" bash-tool
   "WebSearch" web-search-tool
   "WebFetch" web-fetch-tool})

(defn tool-by-name
  "Get Tool instance by name. Returns nil if not found."
  [tool-name]
  (cond
    (contains? tool-registry tool-name)
    ((get tool-registry tool-name))

    (and (string? tool-name) (.startsWith tool-name "mcp__"))
    (mcp-tool tool-name)

    :else nil))

(defn tools-for-agent
  "Get list of Tool instances for an agent given list of tool names.
   Filters out unknown tools."
  [tool-names]
  (keep tool-by-name tool-names))

(defn subagent-tools
  "Get tools for a subagent by name.
   Returns list of Tool objects."
  [subagent-name]
  (let [agent-def (agent.agents/get-agent subagent-name)
        tool-names (:tools agent-def)]
    (tools-for-agent tool-names)))