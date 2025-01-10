(ns chrondb.storage.git
  (:require [chrondb.storage.protocol :as protocol]
            [clojure.java.io :as io]
            [clojure.data.json :as json])
  (:import [org.eclipse.jgit.api Git]
           [org.eclipse.jgit.transport URIish RefSpec]))

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
  "Creates a new bare Git repository at the specified path"
  [path]
  (ensure-directory path)
  (let [temp-path (str path "-temp")
        temp-repo (-> (Git/init)
                      (.setDirectory (io/file temp-path))
                      (.call))]
    (try
      ;; Create initial commit in temp repo
      (-> temp-repo
          (.commit)
          (.setMessage "Initial empty commit")
          (.setAllowEmpty true)
          (.setSign false)
          (.call))
      ;; Create main branch
      (-> temp-repo
          (.branchCreate)
          (.setName "main")
          (.setForce true)
          (.call))
      ;; Create bare repo
      (let [bare-repo (-> (Git/init)
                          (.setDirectory (io/file path))
                          (.setBare true)
                          (.call)
                          (.getRepository))]
        ;; Add bare repo as remote
        (-> temp-repo
            (.remoteAdd)
            (.setName "origin")
            (.setUri (-> (URIish.)
                         (.setScheme "file")
                         (.setPath (.getAbsolutePath (.getDirectory bare-repo)))))
            (.call))
        ;; Push to bare repo with force
        (-> temp-repo
            (.push)
            (.setRemote "origin")
            (.setRefSpecs (java.util.Collections/singletonList
                           (org.eclipse.jgit.transport.RefSpec. "+refs/heads/main:refs/heads/main")))
            (.setForce true)
            (.call))
        bare-repo)
      (finally
        (.close temp-repo)
        (doseq [file (reverse (file-seq (io/file temp-path)))]
          (.delete file))))))

(defn- with-temp-clone [repository f]
  (let [temp-path (str (System/getProperty "java.io.tmpdir") "/chrondb-" (System/currentTimeMillis))
        temp-repo (-> (Git/init)
                      (.setDirectory (io/file temp-path))
                      (.call))]
    (try
      ;; Add bare repo as remote
      (-> temp-repo
          (.remoteAdd)
          (.setName "origin")
          (.setUri (URIish. (str "file://" (.getAbsolutePath (.getDirectory repository)))))
          (.call))
      ;; Fetch from bare repo
      (-> temp-repo
          (.fetch)
          (.setRemote "origin")
          (.call))
      ;; Create and checkout main branch tracking origin/main
      (-> temp-repo
          (.checkout)
          (.setName "main")
          (.setCreateBranch true)
          (.setStartPoint "origin/main")
          (.setUpstreamMode org.eclipse.jgit.api.CreateBranchCommand$SetupUpstreamMode/TRACK)
          (.call))
      ;; Execute function and push changes
      (let [result (f temp-repo)]
        (-> temp-repo
            (.push)
            (.setRemote "origin")
            (.setRefSpecs (java.util.Collections/singletonList
                           (org.eclipse.jgit.transport.RefSpec. "+refs/heads/main:refs/heads/main")))
            (.setForce true)
            (.call))
        result)
      (finally
        (.close temp-repo)
        (doseq [file (reverse (file-seq (io/file temp-path)))]
          (.delete file))))))

(defrecord GitStorage [repository data-dir]
  protocol/Storage

  (save-document [_ document]
    (when-not document
      (throw (Exception. "Document cannot be nil")))
    (when-not repository
      (throw (Exception. "Repository is closed")))
    (with-temp-clone repository
      (fn [git]
        (let [doc-path (str data-dir "/" (:id document) ".json")
              doc-file (io/file (.getDirectory (.getRepository git)) doc-path)]
          (ensure-directory (.getParentFile doc-file))
          (spit doc-file (json/write-str document))
          (-> git
              (.add)
              (.addFilepattern doc-path)
              (.call))
          (-> git
              (.commit)
              (.setMessage "Save document")
              (.setSign false)
              (.call))
          document))))

  (get-document [_ id]
    (when repository
      (try
        (with-temp-clone repository
          (fn [git]
            (let [doc-path (str data-dir "/" id ".json")
                  doc-file (io/file (.getDirectory (.getRepository git)) doc-path)]
              (when (.exists doc-file)
                (json/read-str (slurp doc-file) :key-fn keyword)))))
        (catch Exception _
          nil))))

  (delete-document [_ id]
    (if-not repository
      false
      (try
        (with-temp-clone repository
          (fn [git]
            (let [doc-path (str data-dir "/" id ".json")
                  doc-file (io/file (.getDirectory (.getRepository git)) doc-path)]
              (if (.exists doc-file)
                (do
                  (.delete doc-file)
                  (-> git
                      (.rm)
                      (.addFilepattern doc-path)
                      (.call))
                  (-> git
                      (.commit)
                      (.setMessage "Delete document")
                      (.setSign false)
                      (.call))
                  true)
                false))))
        (catch Exception _
          false))))

  (close [_]
    (when repository
      (.close repository))
    (GitStorage. nil data-dir)))

(defn create-git-storage
  "Creates a new Git storage instance"
  [path]
  (let [data-dir "data"]
    (ensure-directory (str path "/" data-dir))
    (let [repo (create-repository path)]
      (->GitStorage repo data-dir)))) 