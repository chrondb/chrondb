(ns chrondb.test-helpers
  (:require [chrondb.storage.memory :as memory]
            [chrondb.index.lucene :as lucene]
            [clojure.java.io :as io])
  (:import [java.nio.file Files]
           [java.nio.file.attribute FileAttribute]))

(defn create-temp-dir []
  (let [dir (str (Files/createTempDirectory "chrondb-test" (make-array FileAttribute 0)))]
    (-> (io/file dir)
        (.deleteOnExit))
    dir))

(defn delete-directory [path]
  (when (.exists (io/file path))
    (try
      (doseq [f (reverse (file-seq (io/file path)))]
        (try
          (io/delete-file f true)
          (catch Exception e
            (println "Warning: Failed to delete file" (.getPath f) "-" (.getMessage e)))))
      (catch Exception e
        (println "Warning: Failed to clean up directory" path "-" (.getMessage e))))))

(defmacro with-test-data [[storage-sym index-sym] & body]
  `(let [index-dir# (create-temp-dir)
         ~storage-sym (memory/create-memory-storage)
         ~index-sym (lucene/create-lucene-index index-dir#)]
     (try
       ~@body
       (finally
         (try
           (.close ~index-sym)
           (catch Exception e#
             (println "Warning: Failed to close index -" (.getMessage e#))))
         (delete-directory index-dir#))))) 