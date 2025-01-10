(ns chrondb.core.repository
  (:require [chrondb.config :as config])
  (:import (org.eclipse.jgit.internal.storage.dfs InMemoryRepository$Builder DfsRepositoryDescription)
           (org.eclipse.jgit.lib Repository Constants ObjectId)
           (org.eclipse.jgit.revwalk RevWalk)
           (org.eclipse.jgit.treewalk TreeWalk)))

(defn create-memory-repository
  "Cria um repositório Git em memória"
  [path]
  (let [builder (doto (InMemoryRepository$Builder.)
                  (.setRepositoryDescription (DfsRepositoryDescription. path)))
        repo (.build builder)]
    (.create repo)
    repo))

(defn get-head-tree
  "Obtém a árvore do HEAD atual"
  [^Repository repository]
  (when-let [head (.resolve repository Constants/HEAD)]
    (with-open [walk (RevWalk. repository)]
      (let [commit (.parseCommit walk head)]
        (.getTree commit)))))

(defn get-object-content
  "Obtém o conteúdo de um objeto Git"
  [^Repository repository ^ObjectId object-id]
  (with-open [reader (.newObjectReader repository)]
    (let [obj (.open reader object-id)]
      (slurp (.openStream obj))))) 