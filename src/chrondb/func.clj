(ns chrondb.func
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clj-compress.core :as c]
            [chrondb.config :as config])
  (:import (java.io ByteArrayOutputStream)
           (org.eclipse.jgit.internal.storage.dfs InMemoryRepository$Builder DfsRepositoryDescription)
           (org.eclipse.jgit.lib Repository PersonIdent ObjectId Constants FileMode RefUpdate$Result)
           (org.eclipse.jgit.lib CommitBuilder TreeFormatter)
           (org.eclipse.jgit.treewalk TreeWalk)
           (org.eclipse.jgit.revwalk RevWalk)))

(defn create-repository
  "Create a new in-memory repository"
  [& {:keys [branch-name]
      :or   {branch-name config/default-branch-name}}]
  (let [repo (-> (InMemoryRepository$Builder.)
                (.setRepositoryDescription (DfsRepositoryDescription. "chrondb"))
                (.setInitialBranch branch-name)
                (.build))]
    (.create repo)
    (with-open [inserter (.newObjectInserter repo)]
      (let [tree-formatter (TreeFormatter.)
            tree-id (.insert inserter tree-formatter)
            commit (doto (CommitBuilder.)
                    (.setTreeId tree-id)
                    (.setAuthor (PersonIdent. "chrondb" "chrondb@localhost"))
                    (.setCommitter (PersonIdent. "chrondb" "chrondb@localhost"))
                    (.setMessage "Initial commit\n"))
            commit-id (.insert inserter commit)]
        (.flush inserter)
        (let [ref-update (.updateRef repo (str "refs/heads/" branch-name))]
          (.setNewObjectId ref-update commit-id)
          (.setExpectedOldObjectId ref-update (ObjectId/zeroId))
          (.update ref-update))
        (let [ref-update (.updateRef repo Constants/HEAD)]
          (.setNewObjectId ref-update commit-id)
          (.setExpectedOldObjectId ref-update (ObjectId/zeroId))
          (.update ref-update))))
    repo))

(defn save
  "save (insert/update) information in the repository"
  [^Repository repo key value
   & {:keys [branch-name msg commiter]
      :or   {branch-name config/default-branch-name
             msg         "saving content"
             commiter    {:name "chrondb-anonymous"}}}]
  (with-open [inserter (.newObjectInserter repo)
              rw (RevWalk. repo)]
    (let [ref-name (str "refs/heads/" branch-name)
          current-ref (.exactRef repo ref-name)]
      (when-not current-ref
        (let [head-commit (.resolve repo Constants/HEAD)
              ref-update (.updateRef repo ref-name)]
          (.setNewObjectId ref-update head-commit)
          (.setExpectedOldObjectId ref-update (ObjectId/zeroId))
          (.update ref-update)))
      (let [head-ref (.updateRef repo Constants/HEAD)]
        (.link head-ref ref-name)
        (.update head-ref)))

    (let [data->str     (json/write-str value)
          baos (ByteArrayOutputStream.)
          _    (c/compress-data (.getBytes data->str) baos config/compressor-type)
          blob-id      (.insert inserter Constants/OBJ_BLOB (.toByteArray baos))
          head-commit  (when-let [head (.resolve repo Constants/HEAD)]
                        (.parseCommit rw head))
          old-tree     (when head-commit
                        (.getTree head-commit))
          tree-formatter (doto (TreeFormatter.)
                          (.append (str key config/file-ext) FileMode/REGULAR_FILE blob-id))
          tree-id      (.insert inserter tree-formatter)
          head-id      (or (.resolve repo Constants/HEAD) (ObjectId/zeroId))
          commit       (doto (CommitBuilder.)
                        (.setTreeId tree-id)
                        (.setParentId head-id)
                        (.setAuthor (PersonIdent. (:name commiter) "chrondb@localhost"))
                        (.setCommitter (PersonIdent. (:name commiter) "chrondb@localhost"))
                        (.setMessage (str msg "\n")))
          commit-id    (.insert inserter commit)]
      (.flush inserter)
      (let [ref-update (.updateRef repo Constants/HEAD)]
        (.setNewObjectId ref-update commit-id)
        (.setExpectedOldObjectId ref-update head-id)
        (.update ref-update)))))

(defn find-by-key
  "find by key registered in the git repository"
  [^Repository repo key]
  (when-let [head (.resolve repo Constants/HEAD)]
    (with-open [reader (.newObjectReader repo)
                rw (RevWalk. repo)]
      (let [commit (.parseCommit rw head)
            tree (.getTree commit)
            tree-walk (doto (TreeWalk. repo)
                       (.addTree tree)
                       (.setRecursive true))
            file-name (str key config/file-ext)]
        (loop []
          (when (.next tree-walk)
            (if (= (.getNameString tree-walk) file-name)
              (let [blob-id (.getObjectId tree-walk 0)
                    blob (.open reader blob-id)
                    compressed (.getCachedBytes blob)
                    baos (ByteArrayOutputStream.)]
                (c/decompress-data compressed baos config/compressor-type)
                (json/read-str (.toString baos)))
              (recur))))))))

(defn delete-database
  [^Repository repo]
  (.close repo))
