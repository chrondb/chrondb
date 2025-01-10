(ns chrondb.test-helpers
  (:require [chrondb.storage.memory :as memory]
            [chrondb.index.lucene :as lucene]
            [clojure.java.io :as io])
  (:import [java.nio.file Files]
           [java.nio.file.attribute FileAttribute]))

(defn create-temp-dir []
  (str (Files/createTempDirectory "chrondb-test" (make-array FileAttribute 0))))

(defn delete-directory [path]
  (when (.exists (io/file path))
    (doseq [f (reverse (file-seq (io/file path)))]
      (io/delete-file f true))))

(defmacro with-test-data [[storage-sym index-sym] & body]
  `(let [index-dir# (create-temp-dir)
         ~storage-sym (memory/create-memory-storage)
         ~index-sym (lucene/create-lucene-index index-dir#)]
     (try
       ~@body
       (finally
         (try
           (.close ~index-sym)
           (catch Exception _#))
         (delete-directory index-dir#))))) 