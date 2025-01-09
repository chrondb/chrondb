(ns chrondb.func
  (:require [clojure.data.json :as json])
  (:import (org.eclipse.jgit.internal.storage.dfs InMemoryRepository$Builder DfsRepositoryDescription)
           (org.eclipse.jgit.lib CommitBuilder Constants FileMode ObjectId Repository TreeFormatter PersonIdent)
           (org.eclipse.jgit.revwalk RevTree)
           (org.eclipse.jgit.treewalk TreeWalk)))

(defn create-repository
  [path]
  (let [builder (doto (InMemoryRepository$Builder.)
                  (.setRepositoryDescription (DfsRepositoryDescription. path)))
        repo (.build builder)]
    (.create repo)
    repo))

(defn save
  [^Repository repository ^RevTree tree k v]
  (with-open [object-inserter (.newObjectInserter repository)]
    (let [branch (.resolve repository Constants/HEAD)
          blob-str (json/write-str v)
          blob (.getBytes blob-str)
          blob-id (.insert object-inserter Constants/OBJ_BLOB blob)
          
          ;; Create root tree with db tree and other keys
          root-tree-formatter (TreeFormatter.)
          
          ;; Add existing keys (except the one we're updating)
          _ (when tree
              (with-open [tw (doto (TreeWalk. repository)
                              (.addTree tree)
                              (.setRecursive true))]
                (while (.next tw)
                  (let [path (.getPathString tw)
                        mode (.getFileMode tw 0)
                        obj-id (.getObjectId tw 0)]
                    (when (not= path k)
                      (.append root-tree-formatter path mode obj-id))))))
          
          ;; Add the new/updated key
          _ (.append root-tree-formatter k FileMode/REGULAR_FILE blob-id)
          
          root-tree-id (.insert object-inserter root-tree-formatter)
          
          ;; Create and insert commit
          commit (doto (CommitBuilder.)
                  (.setTreeId root-tree-id)
                  (.setMessage (str "save " k))
                  (.setAuthor (PersonIdent. "chrondb" "chrondb@localhost"))
                  (.setCommitter (PersonIdent. "chrondb" "chrondb@localhost"))
                  (.setEncoding Constants/CHARACTER_ENCODING))
          _ (when branch
              (.setParentId commit branch))
          commit-id (.insert object-inserter commit)]
      
      ;; Flush changes and update refs
      (.flush object-inserter)
      (let [ref-update (.updateRef repository Constants/HEAD)]
        (.setNewObjectId ref-update commit-id)
        (.setExpectedOldObjectId ref-update (if branch branch (ObjectId/zeroId)))
        (.update ref-update))
      (let [ref-update (.updateRef repository "refs/heads/main")]
        (.setNewObjectId ref-update commit-id)
        (.setExpectedOldObjectId ref-update (if branch branch (ObjectId/zeroId)))
        (.update ref-update)))))

(defn get-value
  [^Repository repository ^RevTree tree k]
  (with-open [reader (.newObjectReader repository)
              tw (doto (TreeWalk. repository)
                   (.addTree tree)
                   (.setRecursive true))]
    (loop []
      (if (.next tw)
        (let [path (.getPathString tw)]
          (if (= path k)
            (let [obj (.getObjectId tw 0)
                  blob (.openStream (.open reader obj))
                  data (slurp blob)]
              (json/read-str data))
            (recur)))
        nil))))
