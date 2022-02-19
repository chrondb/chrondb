(ns chrondb.api-v1
  "
  JGit javadoc: https://download.eclipse.org/jgit/site/6.0.0.202111291000-r/apidocs/index.html
  "
  (:refer-clojure :exclude [select-keys])
  (:require [clj-jgit.porcelain :as jgit]
            [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [clojure.data.json :as json])
  (:import (java.io File InputStream OutputStream ByteArrayOutputStream)
           (org.eclipse.jgit.api Git)
           (org.eclipse.jgit.lib AnyObjectId Constants FileMode TreeFormatter CommitBuilder PersonIdent RefUpdate$Result ObjectId Repository)
           (org.eclipse.jgit.treewalk TreeWalk)
           (java.nio.charset StandardCharsets)
           (org.eclipse.jgit.revwalk RevWalk)
           (org.eclipse.jgit.storage.file FileRepositoryBuilder)
           (java.time Instant)
           (java.util.zip GZIPInputStream GZIPOutputStream)
           (java.lang AutoCloseable)))
;; TODO: support InMemoryRepository


(set! *warn-on-reflection* true)

(defn any-object-id?
  [x]
  (instance? AnyObjectId x))
(s/def ::tree any-object-id?)

(defn file?
  [x]
  (instance? File x))
(s/def ::db-dir file?)

(defn delete-database
  [db-dir]
  (doseq [^File f (-> db-dir file-seq reverse)]
    (.delete f)))

(defn create-database
  [^File db-dir]
  (let [^Repository repository (-> (FileRepositoryBuilder.)
                                 (doto
                                   (.setInitialBranch "main")
                                   (.setGitDir db-dir))
                                 (.build)
                                 (doto (.create true)))]
    (with-open [object-inserter (.newObjectInserter repository)]
      (let [object-id (.insert object-inserter Constants/OBJ_BLOB
                        (.getBytes (str (Instant/now))
                          StandardCharsets/UTF_8))
            tree-formatter (doto (TreeFormatter.)
                             (.append "db/created-at" FileMode/REGULAR_FILE object-id))
            tree-id (.insert object-inserter tree-formatter)
            commit (doto (CommitBuilder.)
                     (.setAuthor (PersonIdent. "chrondb" "chrondb@localhost"))
                     (.setCommitter (PersonIdent. "chrondb" "chrondb@localhost"))
                     (.setTreeId tree-id)
                     (.setMessage "init"))
            commit-id (.insert object-inserter commit)]
        (.flush object-inserter)
        (-> (.updateRef repository Constants/HEAD)
          (doto (.setExpectedOldObjectId (ObjectId/zeroId))
                (.setNewObjectId commit-id)
                (.setRefLogMessage "World" false))
          (.update))))
    repository))


(defn ^Git connect
  [^File db-dir]
  (let [repository (-> (FileRepositoryBuilder.)
                     (doto (.setGitDir db-dir))
                     (.build))
        ;; Every git repo has a HEAD file
        ;; This file points to a ref file, that contains a sha of a commit
        ;; This commit has a reference to a tree
        branch (.resolve repository Constants/HEAD)]
    {::repository   repository
     ::value-reader (fn [^InputStream in]
                      (io/reader (GZIPInputStream. in)))
     ::read-value   (fn [rdr]
                      (json/read rdr :key-fn keyword))
     ::value-writer (fn [^OutputStream out]
                      (io/writer (GZIPOutputStream. out)))
     ::write-value  (fn [x writer]
                      (json/write x writer))
     ::branch       branch}))

(defn db
  [{::keys [^Repository repository]
    :as    chronn}]
  (let [commit (.parseCommit repository (.resolve repository Constants/HEAD))
        tree (.getTree commit)]
    (assoc chronn
      ::tree tree)))

(defn select-keys
  [{::keys [^Repository repository ^AnyObjectId tree
            value-reader
            read-value]}
   ks]
  (let [tw (TreeWalk/forPath repository (str (first ks))
             ^"[Lorg.eclipse.jgit.lib.AnyObjectId;"
             (into-array AnyObjectId [tree]))]
    (merge {}
      (when tw
        (with-open [in ^AutoCloseable (value-reader (.openStream (.open repository (.getObjectId tw 0))))]
          {(first ks) (read-value in)})))))

(defn save
  [{::keys [^Repository repository ^AnyObjectId branch value-writer write-value]
    :as    chronn} k v]
  (with-open [object-inserter (.newObjectInserter repository)
              rw (RevWalk. repository)]
    (let [^ByteArrayOutputStream baos (with-open [baos (ByteArrayOutputStream.)
                                                  w ^AutoCloseable (value-writer baos)]
                                        (write-value v w)
                                        baos)
          blob (.toByteArray baos)
          object-id (.insert object-inserter Constants/OBJ_BLOB blob)
          tree-formatter (doto (TreeFormatter.)
                           (.append (str k) FileMode/REGULAR_FILE object-id))
          tree-id (.insert object-inserter tree-formatter)
          next-tree (.parseTree rw tree-id)
          commit (doto (CommitBuilder.)
                   (.setAuthor (PersonIdent. "chrondb" "chrondb@localhost"))
                   (.setCommitter (PersonIdent. "chrondb" "chrondb@localhost"))
                   (.setTreeId next-tree)
                   (.setParentId branch)
                   (.setMessage "Hello!"))
          commit-id (.insert object-inserter commit)]
      (.flush object-inserter)
      (let [ru (doto (.updateRef repository Constants/HEAD)
                 (.setExpectedOldObjectId branch)
                 (.setNewObjectId commit-id)
                 (.setRefLogMessage "World" false))
            status (.update ru)]
        (case (str status)
          "FAST_FORWARD"
          {:db-after (assoc chronn
                       ::tree next-tree)}
          ;; RefUpdate$Result/NEW
          "NEW"
          {:db-after (assoc chronn
                       ::tree next-tree)}
          ;; RefUpdate$Result/FORCED
          "FORCED"
          {:db-after (assoc chronn
                       ::tree next-tree)})))))
