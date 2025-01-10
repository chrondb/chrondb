(ns chrondb.storage.git
  (:require [chrondb.storage.protocol :as protocol]
            [clojure.data.json :as json]
            [clojure.java.io :as io])
  (:import [org.eclipse.jgit.lib Repository ObjectInserter ObjectId TreeFormatter FileMode]
           [org.eclipse.jgit.storage.file FileRepositoryBuilder]
           [org.eclipse.jgit.lib RefUpdate]
           [org.eclipse.jgit.lib ObjectReader]
           [org.eclipse.jgit.revwalk RevWalk]
           [org.eclipse.jgit.treewalk TreeWalk]
           [org.eclipse.jgit.api Git]))

(defn ensure-directory [dir]
  (let [file (io/file dir)]
    (when-not (.exists file)
      (if (.mkdirs file)
        (println "Diretório criado:" (.getAbsolutePath file))
        (throw (ex-info (str "Não foi possível criar o diretório: " (.getAbsolutePath file))
                        {:directory dir}))))))

(defn create-repository [path]
  (println "Criando repositório Git em" path)
  (let [repo-dir (io/file path ".git")
        work-dir (io/file path)]
    (println "Criando diretório de trabalho:" (.getAbsolutePath work-dir))
    (ensure-directory work-dir)
    (println "Criando diretório .git:" (.getAbsolutePath repo-dir))
    (ensure-directory repo-dir)
    (println "Inicializando repositório Git...")
    (let [git (-> (Git/init)
                  (.setDirectory work-dir)
                  (.call))
          repo (.getRepository git)]
      (println "Criando commit inicial vazio...")
      (-> git
          (.commit)
          (.setMessage "Initial empty commit")
          (.setAllowEmpty true)
          (.setSign false)
          (.call))
      (println "Repositório Git criado com sucesso")
      repo)))

(defrecord GitStorage [^Repository repo]
  protocol/Storage
  (save-document [_ doc]
    (let [content (json/write-str doc)
          data-file (io/file (.getWorkTree repo) "data")]
      (spit data-file content)
      (let [git (Git. repo)]
        (-> git
            (.add)
            (.addFilepattern "data")
            (.call))
        (-> git
            (.commit)
            (.setMessage "Save document")
            (.setAllowEmpty false)
            (.setSign false)
            (.call))
        doc)))

  (get-document [_ id]
    (let [^ObjectReader reader (.newObjectReader repo)
          ^RevWalk walk (RevWalk. reader)
          commit (.parseCommit walk (.resolve repo "HEAD"))
          ^TreeWalk tree-walk (TreeWalk/forPath repo "data" (.getTree commit))]
      (when tree-walk
        (let [blob-id (.getObjectId tree-walk 0)
              content (String. (.open reader blob-id) "UTF-8")]
          (json/read-str content :key-fn keyword)))))

  (delete-document [_ id]
    (let [^ObjectInserter inserter (.newObjectInserter repo)
          tree (TreeFormatter.)
          tree-id (.insert inserter tree)
          commit (-> repo
                     (.resolve "HEAD")
                     (RevWalk.)
                     (.parseCommit))
          new-commit (-> repo
                         (.newCommit)
                         (.setTreeId tree-id)
                         (.setParentId (.getId commit))
                         (.setMessage "Delete document")
                         (.create inserter))]
      (.flush inserter)
      (let [update (.updateRef repo "refs/heads/main")]
        (.setNewObjectId update new-commit)
        (.update update))
      true))

  (close [_]
    (.close repo)))

(defn create-git-storage [path]
  (println "Criando storage...")
  (let [repo (create-repository path)]
    (->GitStorage repo))) 