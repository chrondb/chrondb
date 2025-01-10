(ns chrondb.storage.git
  (:require [chrondb.storage.protocol :as protocol]
            [clojure.java.io :as io]
            [clojure.data.json :as json])
  (:import [org.eclipse.jgit.api Git]
           [org.eclipse.jgit.transport URIish RefSpec]
           [org.eclipse.jgit.lib ConfigConstants]
           [org.eclipse.jgit.storage.file FileBasedConfig]
           [org.eclipse.jgit.util SystemReader]))

(defn ensure-directory
  "Creates a directory if it doesn't exist"
  [path]
  (let [dir (io/file path)]
    (when-not (.exists dir)
      (if-not (.mkdirs dir)
        (throw (ex-info (str "Could not create directory: " path)
                       {:path path}))
        true))))

(defn- configure-global-git []
  (let [global-config (-> (SystemReader/getInstance)
                         (.getUserConfig))]
    (.setBoolean global-config "commit" nil "gpgsign" false)
    (.unset global-config "gpg" nil "format")
    (.save global-config)))

(defn- configure-repository [repo]
  (configure-global-git)
  (let [config (.getConfig repo)]
    (.unsetSection config ConfigConstants/CONFIG_GPG_SECTION nil)
    (.unsetSection config "gpg" nil)
    (.setBoolean config ConfigConstants/CONFIG_COMMIT_SECTION nil ConfigConstants/CONFIG_KEY_GPGSIGN false)
    (.setBoolean config "commit" nil "gpgsign" false)
    (.unset config ConfigConstants/CONFIG_GPG_SECTION nil "format")
    (.unset config "gpg" nil "format")
    (.setString config ConfigConstants/CONFIG_CORE_SECTION nil ConfigConstants/CONFIG_KEY_FILEMODE "false")
    (.save config)))

(defn- init-repository [path]
  (let [repo (-> (Git/init)
                 (.setDirectory (io/file path))
                 (.call))]
    (configure-repository (.getRepository repo))
    repo))

(defn- create-initial-commit [git]
  (-> git
      (.commit)
      (.setMessage "Initial empty commit")
      (.setAllowEmpty true)
      (.setCommitter "ChronDB" "chrondb@example.com")
      (.setSign false)
      (.call)))

(defn- setup-main-branch [git]
  (-> git
      (.branchCreate)
      (.setName "main")
      (.setForce true)
      (.call))
  (-> git
      (.checkout)
      (.setName "main")
      (.call)))

(defn- cleanup-temp-clone [{:keys [repo path]}]
  (.close repo)
  (try 
    (io/delete-file (io/file path) true)
    (catch Exception _)))

(defn- with-temp-clone [repository f operation-type]
  (let [temp-path (str (System/getProperty "java.io.tmpdir") "/chrondb-" (System/currentTimeMillis))
        git-clone (-> (Git/cloneRepository)
                     (.setURI (str "file://" (.getAbsolutePath (.getDirectory repository))))
                     (.setDirectory (io/file temp-path))
                     (.setBranch "main")
                     (.call))]
    (try
      (println "Cloned repository to:" temp-path)
      (configure-repository (.getRepository git-clone))
      
      ;; Pull antes de fazer qualquer operação
      (println "Pulling from origin/main...")
      (-> git-clone
          (.pull)
          (.setRemote "origin")
          (.setRemoteBranchName "main")
          (.call))
      
      (let [result (f git-clone)
            status (-> git-clone (.status) (.call))]
        (println "\nGit status after operation:")
        (println "Modified:" (.getModified status))
        (println "Added:" (.getAdded status))
        (println "Removed:" (.getRemoved status))
        (println "Untracked:" (.getUntracked status))
        
        ;; Verificar se há arquivos não rastreados
        (let [untracked-files (.getUntracked status)]
          (when (seq untracked-files)
            (println "Adding untracked files:" untracked-files)
            (doseq [file untracked-files]
              (-> git-clone
                  (.add)
                  (.addFilepattern file)
                  (.call)))))
        
        ;; Verificar se há arquivos modificados
        (let [modified-files (.getModified status)]
          (when (seq modified-files)
            (println "Adding modified files:" modified-files)
            (doseq [file modified-files]
              (-> git-clone
                  (.add)
                  (.addFilepattern file)
                  (.call)))))
        
        ;; Verificar se há arquivos removidos
        (let [removed-files (.getRemoved status)]
          (when (seq removed-files)
            (println "Removing files:" removed-files)
            (doseq [file removed-files]
              (-> git-clone
                  (.rm)
                  (.addFilepattern file)
                  (.call)))))
        
        ;; Verificar novamente o status após as operações
        (let [new-status (-> git-clone (.status) (.call))]
          (when (or (seq (.getModified new-status))
                    (seq (.getAdded new-status))
                    (seq (.getRemoved new-status))
                    (seq (.getUntracked new-status)))
            (println "\nChanges detected, committing...")
            
            ;; Commit
            (println "Creating commit...")
            (-> git-clone
                (.commit)
                (.setMessage (case operation-type
                             :save "Save document"
                             :delete "Delete document"
                             "Git operation"))
                (.setCommitter "ChronDB" "chrondb@example.com")
                (.setSign false)
                (.call))
            
            ;; Push com força
            (println "Pushing changes...")
            (-> git-clone
                (.push)
                (.setRemote "origin")
                (.setRefSpecs [(RefSpec. "+refs/heads/main:refs/heads/main")])
                (.setForce true)
                (.call))
            
            (println "Changes committed and pushed.")))
        
        result)
      (finally
        (println "Cleaning up temporary clone...")
        (.close git-clone)
        (cleanup-temp-clone {:repo git-clone :path temp-path})))))

(defn create-repository [path]
  (ensure-directory path)
  (let [git (-> (Git/init)
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
        
        ;; Configurar remote
        (-> temp-git
            (.remoteAdd)
            (.setName "origin")
            (.setUri (URIish. (str "file://" (.getAbsolutePath (.getDirectory repo)))))
            (.call))
        
        ;; Criar diretório de dados
        (ensure-directory (str temp-path "/data"))
        
        ;; Adicionar diretório de dados
        (-> temp-git
            (.add)
            (.addFilepattern "data")
            (.call))
        
        ;; Criar commit inicial vazio
        (-> temp-git
            (.commit)
            (.setMessage "Initial empty commit")
            (.setAllowEmpty true)
            (.setCommitter "ChronDB" "chrondb@example.com")
            (.setSign false)
            (.call))
        
        ;; Criar e configurar branch main
        (-> temp-git
            (.branchCreate)
            (.setName "main")
            (.setForce true)
            (.call))
        
        (-> temp-git
            (.checkout)
            (.setName "main")
            (.call))
        
        ;; Push inicial
        (-> temp-git
            (.push)
            (.setRemote "origin")
            (.setRefSpecs [(RefSpec. "+refs/heads/main:refs/heads/main")])
            (.setForce true)
            (.call))
        
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
          ;; Garantir que o diretório data existe
          (ensure-directory (.getParentFile doc-file))
          (println "Writing document to:" doc-path)
          
          ;; Escrever o documento
          (spit doc-file (json/write-str document))
          (when-not (.exists doc-file)
            (throw (Exception. "Failed to create document file")))
          
          ;; Verificar se o arquivo foi criado corretamente
          (let [content (slurp doc-file)]
            (println "Written content:" content)
            (let [parsed (json/read-str content :key-fn keyword)]
              (println "Parsed content:" parsed)
              (when-not (= parsed document)
                (throw (Exception. "Document verification failed")))))
          
          ;; Adicionar e commitar o arquivo imediatamente
          (println "Adding document to git...")
          (-> git
              (.add)
              (.addFilepattern doc-path)
              (.call))
          
          ;; Verificar status após adicionar
          (let [status (-> git (.status) (.call))]
            (println "Status after add:")
            (println "Modified:" (.getModified status))
            (println "Added:" (.getAdded status))
            (println "Removed:" (.getRemoved status))
            (println "Untracked:" (.getUntracked status)))
          
          ;; Commit
          (println "Creating commit...")
          (-> git
              (.commit)
              (.setMessage "Save document")
              (.setCommitter "ChronDB" "chrondb@example.com")
              (.setSign false)
              (.call))
          
          ;; Push com força
          (println "Pushing changes...")
          (-> git
              (.push)
              (.setRemote "origin")
              (.setRefSpecs [(RefSpec. "+refs/heads/main:refs/heads/main")])
              (.setForce true)
              (.call))
          
          document))
      :save))

  (get-document [_ id]
    (when repository
      (try
        (with-temp-clone repository
          (fn [git]
            (let [doc-path (str data-dir "/" id ".json")
                  doc-file (io/file (.getWorkTree (.getRepository git)) doc-path)]
              (println "Trying to read document from:" doc-path)
              (println "Repository directory:" (.getAbsolutePath (.getWorkTree (.getRepository git))))
              (println "Full document path:" (.getAbsolutePath doc-file))
              
              ;; Pull antes de tentar ler
              (println "Pulling latest changes...")
              (-> git
                  (.pull)
                  (.setRemote "origin")
                  (.setRemoteBranchName "main")
                  (.call))
              
              ;; Verificar se o arquivo existe
              (println "Checking if file exists at:" (.getAbsolutePath doc-file))
              (when (.exists doc-file)
                (println "Document found, reading content...")
                (let [content (slurp doc-file)]
                  (println "Raw content:" content)
                  (when (empty? content)
                    (throw (Exception. "Document file is empty")))
                  
                  (let [parsed (json/read-str content :key-fn keyword)]
                    (println "Parsed content:" parsed)
                    (when-not (:id parsed)
                      (throw (Exception. "Invalid document format: missing id")))
                    parsed)))))
          :get)
        (catch Exception e
          (println "Error getting document:" (.getMessage e))
          (println "Stack trace:" (with-out-str (.printStackTrace e)))
          nil))))

  (delete-document [_ id]
    (if-not repository
      false
      (try
        (with-temp-clone repository
          (fn [git]
            (let [doc-path (str data-dir "/" id ".json")
                  doc-file (io/file (.getWorkTree (.getRepository git)) doc-path)]
              (println "Trying to delete document:" doc-path)
              
              ;; Pull antes de tentar deletar
              (-> git
                  (.pull)
                  (.setRemote "origin")
                  (.setRemoteBranchName "main")
                  (.call))
              
              (if (.exists doc-file)
                (do
                  (println "Document found, deleting...")
                  (.delete doc-file)
                  (-> git
                      (.rm)
                      (.addFilepattern doc-path)
                      (.call))
                  
                  ;; Commit
                  (-> git
                      (.commit)
                      (.setMessage "Delete document")
                      (.setCommitter "ChronDB" "chrondb@example.com")
                      (.setSign false)
                      (.call))
                  
                  ;; Push com força
                  (-> git
                      (.push)
                      (.setRemote "origin")
                      (.setRefSpecs [(RefSpec. "+refs/heads/main:refs/heads/main")])
                      (.setForce true)
                      (.call))
                  
                  true)
                (do
                  (println "Document not found")
                  false))))
          :delete)
        (catch Exception e
          (println "Error deleting document:" (.getMessage e))
          (println "Stack trace:" (with-out-str (.printStackTrace e)))
          false))))

  java.io.Closeable
  (close [_]
    (when repository
      (.close repository))
    (->GitStorage nil data-dir)))

(defn create-git-storage [path]
  (let [data-dir "data"]
    (ensure-directory (str path "/" data-dir))
    (let [repo (create-repository path)]
      (->GitStorage repo data-dir)))) 