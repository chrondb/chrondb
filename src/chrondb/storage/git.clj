(ns chrondb.storage.git
  "Git-based storage implementation for ChronDB.
   Uses JGit for Git operations and provides versioned document storage."
  (:require [chrondb.storage.protocol :as protocol]
            [chrondb.util.logging :as log]
            [chrondb.config :as config]
            [clojure.java.io :as io]
            [clojure.data.json :as json])
  (:import [org.eclipse.jgit.api Git]
           [org.eclipse.jgit.transport URIish RefSpec]
           [org.eclipse.jgit.lib ConfigConstants]
           [org.eclipse.jgit.util SystemReader]))

(defn ensure-directory
  "Creates a directory if it doesn't exist.
   Throws an exception if directory creation fails."
  [path]
  (let [dir (io/file path)]
    (when-not (.exists dir)
      (if-not (.mkdirs dir)
        (throw (ex-info (str "Could not create directory: " path)
                       {:path path}))
        true))))

(defn- configure-global-git
  "Configures global Git settings to disable GPG signing."
  []
  (let [global-config (-> (SystemReader/getInstance)
                         (.getUserConfig))]
    (.setBoolean global-config "commit" nil "gpgsign" false)
    (.unset global-config "gpg" nil "format")
    (.save global-config)))

(defn- configure-repository
  "Configures repository-specific Git settings."
  [repo]
  (configure-global-git)
  (let [config (.getConfig repo)]
    (.setBoolean config ConfigConstants/CONFIG_COMMIT_SECTION nil ConfigConstants/CONFIG_KEY_GPGSIGN false)
    (.setString config ConfigConstants/CONFIG_CORE_SECTION nil ConfigConstants/CONFIG_KEY_FILEMODE "false")
    (.save config)))

(defn- cleanup-temp-clone
  "Cleans up temporary clone by closing repository and deleting directory."
  [{:keys [repo path]}]
  (.close repo)
  (try 
    (io/delete-file (io/file path) true)
    (catch Exception _)))

(defn- handle-git-status
  "Handles Git status by adding/removing files as needed."
  [git status]
  (let [untracked-files (.getUntracked status)
        modified-files (.getModified status)
        removed-files (.getRemoved status)
        missing-files (.getMissing status)]
    
    (when (seq untracked-files)
      (log/log-debug "Adding untracked files:" untracked-files)
      (doseq [file untracked-files]
        (-> git (.add) (.addFilepattern file) (.call))))
    
    (when (seq modified-files)
      (log/log-debug "Adding modified files:" modified-files)
      (doseq [file modified-files]
        (-> git (.add) (.addFilepattern file) (.call))))
    
    (when (seq removed-files)
      (log/log-debug "Removing files:" removed-files)
      (doseq [file removed-files]
        (-> git (.rm) (.addFilepattern file) (.call))))

    (when (seq missing-files)
      (log/log-debug "Removing missing files:" missing-files)
      (doseq [file missing-files]
        (-> git (.rm) (.addFilepattern file) (.call))))))

(defn- commit-and-push
  "Commits changes and pushes to remote repository."
  [git operation-type config-map]
  (-> git
      (.commit)
      (.setMessage (case operation-type
                    :save "Save document"
                    :delete "Delete document"
                    :init "Initial empty commit"
                    "Git operation"))
      (.setCommitter (get-in config-map [:git :committer-name])
                    (get-in config-map [:git :committer-email]))
      (.setSign (get-in config-map [:git :sign-commits]))
      (.call))
  
  (log/log-info "Pushing changes...")
  (-> git
      (.push)
      (.setRemote "origin")
      (.setRefSpecs [(RefSpec. (str "+refs/heads/" (get-in config-map [:git :default-branch]) 
                                   ":refs/heads/" (get-in config-map [:git :default-branch])))])
      (.setForce true)
      (.call)))

(defn- with-temp-clone
  "Executes operations in a temporary clone of the repository.
   Handles cloning, pulling, committing, and cleanup."
  [repository f operation-type]
  (let [config-map (config/load-config)
        temp-path (str (System/getProperty "java.io.tmpdir") "/chrondb-" (System/currentTimeMillis))
        git-clone (-> (Git/cloneRepository)
                     (.setURI (str "file://" (.getAbsolutePath (.getDirectory repository))))
                     (.setDirectory (io/file temp-path))
                     (.setBranch (get-in config-map [:git :default-branch]))
                     (.call))]
    (try
      (log/log-info "Cloned repository to:" temp-path)
      (configure-repository (.getRepository git-clone))
      
      (log/log-info "Pulling from origin...")
      (-> git-clone
          (.pull)
          (.setRemote "origin")
          (.setRemoteBranchName (get-in config-map [:git :default-branch]))
          (.call))
      
      (let [result (f git-clone)
            status (-> git-clone (.status) (.call))]
        (log/log-git-status status)
        (handle-git-status git-clone status)
        
        (let [new-status (-> git-clone (.status) (.call))]
          (when (or (seq (.getModified new-status))
                    (seq (.getAdded new-status))
                    (seq (.getRemoved new-status))
                    (seq (.getUntracked new-status)))
            (log/log-info "Changes detected, committing...")
            (commit-and-push git-clone operation-type config-map)))
        
        result)
      (finally
        (log/log-info "Cleaning up temporary clone...")
        (.close git-clone)
        (cleanup-temp-clone {:repo git-clone :path temp-path})))))

(defn create-repository
  "Creates a new Git repository for document storage.
   Initializes the repository with an empty commit and configures it."
  [path]
  (ensure-directory path)
  (let [config-map (config/load-config)
        git (-> (Git/init)
                (.setDirectory (io/file path))
                (.setBare true)
                (.call))
        repo (.getRepository git)]
    (configure-repository repo)
    (let [temp-path (str (System/getProperty "java.io.tmpdir") "/chrondb-init-" (System/currentTimeMillis))
          temp-git (-> (Git/init)
                      (.setDirectory (io/file temp-path))
                      (.call))]
      (try
        (configure-repository (.getRepository temp-git))
        
        (-> temp-git
            (.remoteAdd)
            (.setName "origin")
            (.setUri (URIish. (str "file://" (.getAbsolutePath (.getDirectory repo)))))
            (.call))
        
        (ensure-directory (str temp-path "/" (get-in config-map [:storage :data-dir])))
        
        (-> temp-git
            (.add)
            (.addFilepattern (get-in config-map [:storage :data-dir]))
            (.call))
        
        (commit-and-push temp-git :init config-map)
        
        (finally
          (.close temp-git)
          (cleanup-temp-clone {:repo temp-git :path temp-path}))))
    repo))

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
              doc-file (io/file (.getWorkTree (.getRepository git)) doc-path)]
          (ensure-directory (.getParentFile doc-file))
          (log/log-debug "Writing document to:" doc-path)
          
          (spit doc-file (json/write-str document))
          (when-not (.exists doc-file)
            (throw (Exception. "Failed to create document file")))
          
          (let [content (slurp doc-file)
                parsed (json/read-str content :key-fn keyword)]
            (when-not (= parsed document)
              (throw (Exception. "Document verification failed"))))
          
          document))
      :save))

  (get-document [_ id]
    (when-not repository
      (throw (Exception. "Repository is closed")))
    (with-temp-clone repository
      (fn [git]
        (let [doc-path (str data-dir "/" id ".json")
              doc-file (io/file (.getWorkTree (.getRepository git)) doc-path)]
          (when (.exists doc-file)
            (try
              (json/read-str (slurp doc-file) :key-fn keyword)
              (catch Exception e
                (throw (ex-info "Failed to read document" {:id id} e)))))))
      :read))

  (delete-document [_ id]
    (when-not repository
      (throw (Exception. "Repository is closed")))
    (with-temp-clone repository
      (fn [git]
        (let [doc-path (str data-dir "/" id ".json")
              doc-file (io/file (.getWorkTree (.getRepository git)) doc-path)]
          (if (.exists doc-file)
            (do
              (.delete doc-file)
              (when (.exists doc-file)
                (throw (Exception. "Failed to delete document file")))
              true)
            false)))
      :delete))

  (close [_]
    (when repository
      (.close repository)
      nil)))

(defn create-git-storage
  "Creates a new instance of GitStorage.
   Takes a path for the Git repository and optionally a data directory path.
   If data-dir is not provided, uses the one from config.
   Returns: A new GitStorage instance."
  ([path]
   (let [config-map (config/load-config)]
     (create-git-storage path (get-in config-map [:storage :data-dir]))))
  ([path data-dir]
   (->GitStorage (create-repository path) data-dir))) 