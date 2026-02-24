(ns agent.orchestration
  "Subagent orchestration and coordination."
  (:require [clojure.string :as str]
            [taoensso.timbre :as log]
            [agent.core :as core]
            [agent.agents :as agents]
            [agent.tools :as tools]
            [agent.utils :as utils])
  (:import [com.anthropic.models.messages TextBlock]))

(defn extract-text-content
  "Extract text from Message content blocks.
   Returns concatenated text from all TextBlock objects."
  [message]
  (let [content (.content message)
        text-blocks (filter #(instance? TextBlock %) content)]
    (apply str (map #(.text %) text-blocks))))

(defn run-subagent
  "Run a subagent with given name and task description.
   client - AnthropicClient
   subagent-name - string name of subagent (docs_researcher, repo_analyzer, web_researcher)
   task-description - string task for the subagent
   Returns the text result from the subagent."
  [client subagent-name task-description]
  (let [agent-def (agents/get-agent subagent-name)]
    (when-not agent-def
      (throw (ex-info (str "Unknown subagent: " subagent-name)
                      {:subagent-name subagent-name})))
    (let [prompt (:prompt agent-def)
          tool-names (:tools agent-def)
          subagent-tools (tools/tools-for-agent tool-names)]
      (log/info "Starting subagent" subagent-name "with task:" task-description)
      (let [result (core/run-agent-loop client prompt subagent-tools task-description)]
        (extract-text-content result)))))

(defn spawn-subagent
  "Spawn a subagent as part of a Task tool execution.
   Returns map with :content (result text) and optionally :is-error."
  [client subagent-type description]
  (try
    (let [result (run-subagent client subagent-type description)]
      {:content result})
    (catch Exception e
      (log/error "Failed to spawn subagent" subagent-type ":" (.getMessage e))
      {:content (str "Error spawning subagent " subagent-type ": " (.getMessage e))
       :is-error true})))