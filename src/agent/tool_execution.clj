(ns agent.tool-execution
  "Tool execution handlers for the multi-agent system.
   Implements Skill, Task, Write, Bash, WebSearch, WebFetch, and MCP tools."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.java.shell :as shell]
            [clj-http.client :as http]
            [cheshire.core :as json]
            [taoensso.timbre :as log]
            [agent.agents :as agents])
  (:import [java.util.concurrent TimeUnit]
           [java.io BufferedReader InputStreamReader]))

(defmulti execute-tool
  "Execute a tool based on tool name.
   Returns a map with :content (string result) and optionally :is-error (boolean)."
  (fn [tool-name tool-input client] tool-name))

(defmethod execute-tool "Skill"
  [_ input client]
  (let [skill (get input "skill")
        args (get input "args")]
    (log/info "Executing Skill tool:" skill "args:" args)
    {:content (str "Skill '" skill "' executed with args: " args)}))

(defmethod execute-tool "Task"
  [_ input client]
  (let [subagent-type (get input "subagent_type")
        description (get input "description")
        prompt (get input "prompt")]
    (log/info "Spawning subagent:" subagent-type "description:" description)
    (try
      (let [run-subagent-fn (requiring-resolve 'agent.core/run-subagent)]
        (if run-subagent-fn
          (let [result (run-subagent-fn client subagent-type description)]
            {:content result})
          {:content (str "Error: run-subagent function not found")
           :is-error true}))
      (catch Exception e
        (log/error e "Failed to spawn subagent")
        {:content (str "Error spawning subagent '" subagent-type "': " (.getMessage e))
         :is-error true}))))

(defmethod execute-tool "Write"
  [_ input client]
  (let [file-path (get input "file_path")
        content (get input "content")
        file (io/file file-path)
        current-dir (io/file (System/getProperty "user.dir"))
        canonical-file (.getCanonicalPath file)
        canonical-dir (.getCanonicalPath current-dir)]
    (log/info "Writing file:" canonical-file)
    (if (.startsWith canonical-file canonical-dir)
      (try
        (io/make-parents file)
        (spit file content)
        {:content (str "File written successfully: " canonical-file)}
        (catch Exception e
          {:content (str "Error writing file: " (.getMessage e))
           :is-error true}))
      {:content (str "Error: file path must be within project directory")
       :is-error true})))

(defmethod execute-tool "Bash"
  [_ input client]
  (let [command (get input "command")
        working-dir (get input "working_directory")
        dir (if (and working-dir (not (str/blank? working-dir)))
              (io/file working-dir)
              (io/file "."))]
    (log/info "Executing bash command:" command "in dir:" dir)
    (try
      (let [process (shell/sh "bash" "-c" command :dir dir)
            exit (:exit process)
            out (:out process)
            err (:err process)]
        (if (zero? exit)
          {:content (str "Command succeeded:\n" out (when-not (str/blank? err) (str "\nStderr:\n" err)))}
          {:content (str "Command failed with exit code " exit ":\n" err)
           :is-error true}))
      (catch Exception e
        {:content (str "Error executing command: " (.getMessage e))
         :is-error true}))))

(defmethod execute-tool "WebSearch"
  [_ input client]
  (let [query (get input "query")
        max-results (or (get input "max_results") 5)
        url (str "https://api.duckduckgo.com/?q=" (java.net.URLEncoder/encode query) "&format=json&no_html=1")]
    (log/info "Web search query:" query)
    (try
      (let [response (http/get url {:socket-timeout 10000 :conn-timeout 10000})
            body (:body response)
            data (json/parse-string body true)
            abstract (:AbstractText data)
            results (concat (when (not (str/blank? abstract)) [{:title "Abstract" :content abstract}])
                            (take max-results (:RelatedTopics data)))]
        {:content (str "Search results for '" query "':\n"
                       (str/join "\n---\n"
                         (map (fn [r] (str (:Text r) "\n" (:Result r))) results)))})
      (catch Exception e
        {:content (str "Error performing web search: " (.getMessage e))
         :is-error true}))))

(defmethod execute-tool "WebFetch"
  [_ input client]
  (let [url (get input "url")
        prompt (get input "prompt")]
    (log/info "Fetching URL:" url)
    (try
      (let [response (http/get url {:socket-timeout 10000 :conn-timeout 10000 :as :text})
            body (:body response)
            trimmed (subs body 0 (min (count body) 5000))]
        {:content (str "Fetched content from " url ":\n" trimmed)})
      (catch Exception e
        {:content (str "Error fetching URL: " (.getMessage e))
         :is-error true}))))

;; MCP tools
(defmethod execute-tool :default
  [tool-name input client]
  (if (str/starts-with? tool-name "mcp__")
    (do
      (log/info "Executing MCP tool:" tool-name "input:" input)
      {:content (str "MCP tool '" tool-name "' executed (mock)")})
    {:content (str "Unknown tool '" tool-name "'")
     :is-error true}))

(defn execute-tool-use-block
  "Execute a tool use block and return tool result map.
   block is a ToolUseBlock object."
  [block client]
  (let [tool-name (.name block)
        tool-input (.input block)
        tool-id (.id block)]
    (log/debug "Executing tool" tool-name "id:" tool-id)
    (let [result (execute-tool tool-name tool-input client)
          result-map {:type "tool_result"
                      :tool_use_id tool-id
                      :content (:content result)}]
      (when (:is-error result)
        (assoc result-map :is_error true))
      result-map)))