(ns agent.agents
  "Definitions and management of subagents (docs_researcher, repo_analyzer, web_researcher)."
  (:require [agent.prompts :as prompts]
            [clojure.string :as str]))

(defrecord AgentDefinition [name description prompt tools model])

(defn create-agent-definition
  "Create an AgentDefinition record."
  [name description prompt tools model]
  (->AgentDefinition name description prompt tools model))

(defn load-agent-definitions
  "Load all subagent definitions with their prompts."
  []
  (let [prompts-map (prompts/load-all-prompts)]
    {"docs_researcher" (create-agent-definition
                        "docs_researcher"
                        "Finds and extracts information from official documentation sources."
                        (:docs-researcher prompts-map)
                        ["WebSearch" "WebFetch"]
                        "haiku")
     "repo_analyzer" (create-agent-definition
                      "repo_analyzer"
                      "Analyzes code repositories for structure, examples, and implementation details."
                      (:repo-analyzer prompts-map)
                      ["WebSearch" "Bash"]
                      "haiku")
     "web_researcher" (create-agent-definition
                       "web_researcher"
                       "Finds articles, videos, and community content."
                       (:web-researcher prompts-map)
                       ["WebSearch" "WebFetch"]
                       "haiku")}))

(defn get-agent
  "Get agent definition by name."
  [agent-name]
  (get (load-agent-definitions) agent-name))

(defn list-agents
  "Return list of available agent names."
  []
  (keys (load-agent-definitions)))

(defn agent-tools
  "Get tools list for an agent."
  [agent-name]
  (:tools (get-agent agent-name)))

(defn agent-model
  "Get model for an agent."
  [agent-name]
  (:model (get-agent agent-name)))