(ns agent.utils
  "Message display formatting and utilities.
   Port of utils.cljs functionality to Clojure JVM."
  (:require [clojure.string :as str]
            [taoensso.timbre :as log])
  (:import [com.anthropic.models.messages Message ContentBlock TextBlock ToolUseBlock]))

;; Track subagent names by their tool_use_id (similar to atom in ClojureScript)
(def subagent-registry (atom {}))

(defn truncate
  "Truncate a value for display."
  [value max-length]
  (let [text (str value)]
    (if (> (count text) max-length)
      (str (subs text 0 max-length) "...")
      text)))

(defn format-input
  "Format tool input for readable display."
  ([input-dict] (format-input input-dict 200))
  ([input-dict max-length]
   (if (or (nil? input-dict) (empty? input-dict))
     "{}"
     (let [parts (for [[key value] input-dict]
                   (let [val-str (str value)
                         trimmed (if (> (count val-str) 50)
                                   (str (subs val-str 0 50) "...")
                                   val-str)]
                     (str key "=" trimmed)))]
       (truncate (str/join ", " parts) max-length)))))

(defn get-agent-label
  "Determine source agent label with color codes.
   message is a Message object."
  [message]
  (let [parent-id (.parentToolUseId message)] ; Assuming getter method
    (if parent-id
      (let [subagent-name (get @subagent-registry parent-id "unknown")]
        (str "\u001b[35m[Subagent " subagent-name "]\u001b[0m"))
      "\u001b[36m[Main]\u001b[0m")))

(defn display-text-block
  "Display a text block from Claude response."
  [block]
  (let [text (.text block)] ; TextBlock getter
    (println (str "\u001b[1mClaude\u001b[0m: " text "\n"))))

(defn display-tool-use-block
  "Display a tool use block from Claude response."
  [block agent-label]
  (let [tool-name (.name block)
        tool-input (.input block)
        tool-id (.id block)]
    (if (= tool-name "Task")
      (let [subagent-type (or (get tool-input "subagent_type") "unknown")
            description (or (get tool-input "description") "")]
        (when tool-id
          (swap! subagent-registry assoc tool-id subagent-type))
        (println (str agent-label " 🚀 Spawning subagent: \u001b[1m" subagent-type "\u001b[0m"))
        (when (not (str/blank? description))
          (println (str "   Description: " description))))
      (let [short-id (if tool-id (subs tool-id 0 8) "unknown")]
        (println (str agent-label " 🔧 \u001b[1m" tool-name "\u001b[0m (id: " short-id ")"))
        (println (str "   Input: " (format-input tool-input)))))))

(defn display-message
  "Display an AssistantMessage with color-coded agent labels.
   message is a Message object with content blocks."
  [message]
  (let [agent-label (get-agent-label message)
        content (.content message)] ; getContent() method
    (doseq [block content]
      (cond
        (instance? ToolUseBlock block)
        (display-tool-use-block block agent-label)

        (instance? TextBlock block)
        (display-text-block block)

        :else
        (log/warn "Unknown content block type:" (class block))))))

(defn reset-subagent-registry!
  "Clear the subagent registry."
  []
  (reset! subagent-registry {}))