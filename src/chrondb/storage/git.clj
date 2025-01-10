(ns chrondb.storage.git
  (:require [chrondb.storage.protocol :as protocol]
            [clojure.java.io :as io]
            [clojure.data.json :as json])
  (:import [org.eclipse.jgit.api Git]))

(defn ensure-directory
  "Creates a directory if it doesn't exist"
  [path]
  (let [dir (io/file path)]
    (when-not (.exists dir)
      (if-not (.mkdirs dir)
        (throw (ex-info (str "Could not create directory: " path)
                        {:path path}))
        true))))

(defn create-repository
  "Creates a new Git repository at the specified path"
  [path]
  (ensure-directory path)
  (let [git (-> (Git/init)
                (.setDirectory (io/file path))
                (.call))
        repo (.getRepository git)]
    (-> git
        (.commit)
        (.setMessage "Initial empty commit")
        (.setAllowEmpty true)
        (.setSign false)
        (.call))
    repo))

(defrecord GitStorage [repository data-dir]
  protocol/Storage

  (save-document [_ document]
    (when-not document
      (throw (Exception. "Document cannot be nil")))
    (when-not repository
      (throw (Exception. "Repository is closed")))
    (let [git (Git. repository)
          doc-path (str data-dir "/" (:id document) ".json")
          doc-file (io/file doc-path)]
      (ensure-directory (.getParentFile doc-file))
      (spit doc-path (json/write-str document))
      (-> git
          (.add)
          (.addFilepattern (str "data/" (:id document) ".json"))
          (.call))
      (-> git
          (.commit)
          (.setMessage "Save document")
          (.setSign false)
          (.call))
      document))

  (get-document [_ id]
    (when repository
      (try
        (let [doc-path (str data-dir "/" id ".json")]
          (when (.exists (io/file doc-path))
            (json/read-str (slurp doc-path) :key-fn keyword)))
        (catch Exception _
          nil))))

  (delete-document [_ id]
    (when repository
      (try
        (let [git (Git. repository)
              doc-path (str data-dir "/" id ".json")
              file (io/file doc-path)]
          (if (.exists file)
            (do
              (.delete file)
              (-> git
                  (.rm)
                  (.addFilepattern (str "data/" id ".json"))
                  (.call))
              (-> git
                  (.commit)
                  (.setMessage "Delete document")
                  (.setSign false)
                  (.call))
              true)
            false))
        (catch Exception _
          false))))

  (close [_]
    (when repository
      (.close repository))
    (GitStorage. nil data-dir)))

(defn create-git-storage
  "Creates a new Git storage instance"
  [path]
  (let [repo (create-repository path)
        data-dir (str path "/data")]
    (ensure-directory data-dir)
    (->GitStorage repo data-dir))) 