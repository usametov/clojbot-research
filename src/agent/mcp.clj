(ns agent.mcp
  "MCP (Model Context Protocol) server configuration and management.
   Currently supports Notion MCP server integration."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [taoensso.timbre :as log]))

(defn notion-mcp-config
  "Create configuration for Notion MCP server.
   Returns a map with :command, :args, and :env."
  [notion-token]
  (when notion-token
    {:command "npx"
     :args ["-y" "@notionhq/notion-mcp-server"]
     :env {:NOTION_TOKEN notion-token}}))

(defn mcp-servers-config
  "Create MCP servers configuration map.
   Currently only supports Notion."
  [notion-token]
  (let [notion-config (notion-mcp-config notion-token)]
    (if notion-config
      {"notion" notion-config}
      {})))

(defn start-mcp-server
  "Start an MCP server process.
   Returns a Process object."
  [config]
  (let [process-builder (ProcessBuilder. ^java.util.List (into [(get config :command)]
                                                               (get config :args)))]
    (doto (.environment process-builder)
      (.putAll (get config :env {})))
    (.start process-builder)))

(defn stop-mcp-server
  "Stop an MCP server process."
  [process]
  (when process
    (.destroy process)
    (log/info "Stopped MCP server")))

(defn mcp-tool-names
  "Get list of MCP tool names for configuration.
   Based on the existing system: notion API tools."
  []
  ["mcp__notion__API-post-search"
   "mcp__notion__API-patch-block-children"])