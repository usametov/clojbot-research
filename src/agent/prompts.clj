(ns agent.prompts
  "Utilities for loading prompt files from resources."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]))

(def prompts-dir "prompts")

(defn load-prompt
  "Load a prompt file from resources/prompts/ directory.
   Returns the file content as a trimmed string."
  [filename]
  (let [resource-path (str prompts-dir "/" filename)
        resource (io/resource resource-path)]
    (if resource
      (-> resource slurp str/trim)
      (throw (ex-info (str "Prompt file not found: " filename)
                      {:filename filename :resource-path resource-path})))))

(defn load-all-prompts
  "Load all prompt files used by the system.
   Returns a map with keys :main-agent, :docs-researcher, :repo-analyzer, :web-researcher."
  []
  {:main-agent (load-prompt "main_agent.md")
   :docs-researcher (load-prompt "docs_researcher.md")
   :repo-analyzer (load-prompt "repo_analyzer.md")
   :web-researcher (load-prompt "web_researcher.md")})