(ns agent.core
  "Main entry point for Clojure JVM multi-agent system using Anthropic Java SDK.
   Implements multi-agent orchestration with subagents (docs_researcher, repo_analyzer, web_researcher),
   MCP server integration (Notion), and interactive CLI interface."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.pprint :refer [pprint]]
            [manifold.deferred :as d]
            [manifold.stream :as s]
            [taoensso.timbre :as log]
            [aero.core :as aero]
            [agent.prompts :as prompts]
            [agent.mcp :as mcp]
            [agent.agents :as agents]
            [agent.tools :as tools]
            [agent.tool-execution :as tool-exec]
            [agent.utils :as utils])
  (:import [com.anthropic.client AnthropicClient]
           [com.anthropic.client.okhttp AnthropicOkHttpClient]
           [com.anthropic.models.messages Message MessageCreateParams Model Tool ToolUseBlock ToolResultBlockParam ContentBlock TextBlock]
           [com.anthropic.core JsonValue]
           [java.util Map List ArrayList]))

;; Configuration
(defonce config (atom nil))

(defn load-config!
  "Load configuration from resources/config.edn using aero.
   Environment variables are resolved via #env tags."
  []
  (let [cfg (aero/read-config (io/resource "config.edn"))]
    (reset! config cfg)
    (log/info "Configuration loaded")
    cfg))

(defn get-config
  "Get configuration value at path (keyword or vector of keywords).
   Returns default if not found."
  ([path] (get-config path nil))
  ([path default]
   (get-in @config path default)))

(defn create-client
  "Create an AnthropicClient instance from configuration."
  []
  (let [builder (AnthropicOkHttpClient/builder)
        api-key (get-config :api-key)
        base-url (get-config :base-url)]
    (when api-key
      (.apiKey builder api-key))
    (when base-url
      (.baseUrl builder base-url))
    (.build builder)))

(defn create-message-params
  "Create MessageCreateParams for a user query with system prompt and tools.
   messages is a list of previous messages (alternating user/assistant).
   Each message is [role content] where role is :user or :assistant.
   Content can be a string (for simple text) or a list of ContentBlock objects.
   tools is a list of Tool objects."
  [system-prompt tools messages]
  (let [builder (MessageCreateParams/builder)]
    (.model builder Model/CLAUDE_OPUS_4_6)
    (.maxTokens builder 4096)
    (.system builder system-prompt)
    (doseq [tool tools]
      (.addTool builder tool))
    (doseq [[role content] messages]
      (cond
        (string? content)
        (case role
          :user (.addUserMessage builder content)
          :assistant (.addAssistantMessage builder content))

        (instance? java.util.Collection content)
        (case role
          :user (.addUserMessage builder ^java.util.Collection content)
          :assistant (.addAssistantMessage builder ^java.util.Collection content))

        :else
        (throw (ex-info "Invalid message content type" {:role role :content content}))))
    (.build builder)))

(defn extract-tool-use-blocks
  "Extract tool use blocks from Message content.
   Returns a list of ToolUseBlock objects."
  [message]
  (let [content (.content message)]
    (filter #(instance? ToolUseBlock %) content)))

(defn process-tool-use-blocks
  "Process tool use blocks and return tool results.
   Returns a list of tool result maps."
  [client tool-use-blocks]
  (map #(tool-exec/execute-tool-use-block % client) tool-use-blocks))

(defn tool-results->content
  "Convert tool result maps to ContentBlock list for MessageCreateParams.
   Each result map should have :tool_use_id and :content."
  [tool-results]
  (let [content-blocks (ArrayList.)]
    (doseq [result tool-results]
      (let [tool-result-block (ToolResultBlockParam/builder)
            tool-use-id (:tool_use_id result)
            content (:content result)]
        (.toolUseId tool-result-block tool-use-id)
        (.content tool-result-block content)
        (.add content-blocks (.build tool-result-block))))
    content-blocks))

(defn run-agent-loop
  "Run agent loop for a single user query.
   client - AnthropicClient
   system-prompt - string
   tools - list of Tool objects
   user-query - string
   Returns the final message after all tool executions."
  [client system-prompt tools user-query]
  (loop [messages [[:user user-query]]]
    (let [params (create-message-params system-prompt tools messages)
          response (.create (.messages client) params)
          tool-use-blocks (extract-tool-use-blocks response)]
      (utils/display-message response)
      (if (empty? tool-use-blocks)
        response
        (let [tool-results (process-tool-use-blocks client tool-use-blocks)
              assistant-content (.content response)
              ;; Add assistant message with tool use blocks
              new-messages (conj messages [:assistant assistant-content])
              ;; Add user message with tool results
              final-messages (conj new-messages [:user (tool-results->content tool-results)])]
          (recur final-messages))))))

(defn extract-text-content
  "Extract text from Message content blocks.
   Returns concatenated text from all TextBlock objects."
  [message]
  (let [content (.content message)
        text-blocks (filter #(instance? TextBlock %) content)]
    (apply str (map #(.text %) text-blocks))))

(defn run-subagent
  "Run a subagent with given name and task description.
   Returns the text result from the subagent."
  [client subagent-name task-description]
  (let [agent-def (agents/get-agent subagent-name)
        prompt (:prompt agent-def)
        tools (tools/subagent-tools subagent-name)]
    (log/info "Starting subagent" subagent-name "with task:" task-description)
    (let [result (run-agent-loop client prompt tools task-description)]
      (extract-text-content result))))

(defn interactive-loop
  "Run interactive CLI loop for user queries."
  [client system-prompt tools]
  (println "Starting conversation session.")
  (println "Type 'exit' to quit\n")
  (utils/reset-subagent-registry!)
  (loop []
    (print "You: ")
    (flush)
    (let [input (read-line)]
      (cond
        (nil? input) (println "\nGoodbye!")
        (= "exit" (str/lower-case input)) (println "Goodbye!")
        :else
        (do
          (log/debug "User query:" input)
          (run-agent-loop client system-prompt tools input)
          (recur))))))

(defn -main
  "Main entry point."
  [& args]
  (load-config!)
  (log/info "Starting multi-agent system")

  (let [client (create-client)
        system-prompt (prompts/load-prompt "main_agent.md")
        notion-token (get-config :notion-token)
        mcp-config (mcp/mcp-servers-config notion-token)
        all-tools (tools/all-tools)
        api-key (get-config :api-key)]

    (when (nil? api-key)
      (log/error "ANTHROPIC_API_KEY not configured")
      (System/exit 1))

    (log/info "MCP servers configured:" (keys mcp-config))
    (log/info "Tools available:" (map #(.name %) all-tools))
    (log/info "System initialized, starting interactive loop")
    (interactive-loop client system-prompt all-tools)))