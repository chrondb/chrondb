(ns chrondb.core
  (:require [chrondb.api.server :as server]
            [chrondb.storage.memory :as memory]
            [chrondb.index.lucene :as lucene]
            [clojure.java.io :as io]))

(defn ensure-data-directories []
  (let [data-dir "data"
        index-dir (str data-dir "/index")]
    (when-not (.exists (io/file data-dir))
      (.mkdirs (io/file data-dir)))
    (when-not (.exists (io/file index-dir))
      (.mkdirs (io/file index-dir)))))

(defn -main [& [port]]
  (ensure-data-directories)
  (let [storage (memory/create-memory-storage)
        index (lucene/create-lucene-index "data/index")
        port (Integer/parseInt (or port "3000"))]
    (server/start-server storage index port)))