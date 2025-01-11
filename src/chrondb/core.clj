(ns chrondb.core
  "Core namespace for ChronDB - A chronological database with Git-like versioning"
  (:require [chrondb.api.server :as server]
            [chrondb.storage.memory :as memory]
            [chrondb.index.lucene :as lucene]
            [clojure.java.io :as io]))

(defn ensure-data-directories
  "Creates necessary data directories for ChronDB if they don't exist.
   This includes the main data directory and the index subdirectory."
  []
  (let [data-dir "data"
        index-dir (str data-dir "/index")]
    (when-not (.exists (io/file data-dir))
      (.mkdirs (io/file data-dir)))
    (when-not (.exists (io/file index-dir))
      (.mkdirs (io/file index-dir)))))

(defn -main
  "Entry point for the ChronDB application.
   Initializes the storage and index components and starts the HTTP server.
   
   Parameters:
   - port: Optional port number for the HTTP server (default: 3000)"
  [& [port]]
  (ensure-data-directories)
  (let [storage (memory/create-memory-storage)
        index (lucene/create-lucene-index "data/index")
        port (Integer/parseInt (or port "3000"))]
    (server/start-server storage index port)))