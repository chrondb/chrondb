(ns chrondb.api-v1
  "
  JGit javadoc: https://download.eclipse.org/jgit/site/6.0.0.202111291000-r/apidocs/index.html
  "
  (:refer-clojure :exclude [select-keys])
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [clojure.string :as string])
  (:import (java.io ByteArrayOutputStream File InputStream OutputStream)
           (java.lang AutoCloseable)
           (java.net URI)
           (java.time Clock Instant)
           (org.eclipse.jgit.errors IncorrectObjectTypeException)
           (org.eclipse.jgit.internal.storage.dfs InMemoryRepository$Builder)
           (org.eclipse.jgit.lib AnyObjectId CommitBuilder BaseRepositoryBuilder CommitBuilder Constants FileMode
                                 ObjectId PersonIdent Repository TreeFormatter ObjectReader ObjectInserter)
           (org.eclipse.jgit.revwalk RevBlob RevCommit RevTree RevWalk)
           (org.eclipse.jgit.storage.file FileRepositoryBuilder)
           (org.eclipse.jgit.treewalk TreeWalk)
           (org.eclipse.jgit.treewalk.filter PathFilterGroup)))
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


(defmulti db-uri->repository-destroyer ::scheme)


(defn parse-db-uri
  [db-uri]
  (let [uri (URI/create db-uri)
        scheme (.getScheme uri)
        uri (URI/create (.getRawSchemeSpecificPart uri))]
    (when-not (= scheme "chrondb")
      (throw (ex-info (str "Only uri's starting with 'chrondb' are supported. Actual: " scheme)
               {:cognitect.anomalies/category :cognitect.anomalies/unsupported
                ::db-uri                      db-uri})))
    {::scheme (.getScheme uri)
     ::db-uri db-uri
     ::path   (subs (.getRawSchemeSpecificPart uri) 2)}))


(defn delete-database
  [db-uri]
  (db-uri->repository-destroyer (parse-db-uri db-uri)))

(defonce *memory-repository
  (atom {}))

(defmethod db-uri->repository-destroyer "mem"
  [{::keys [path]}]
  (swap! *memory-repository
    (fn [memory-repository]
      (when-let [^Repository repository (get memory-repository path)]
        (.close repository))
      (dissoc memory-repository path))))



(defmulti ^BaseRepositoryBuilder db-uri->repository-builder ::scheme)

(defmethod db-uri->repository-builder "mem"
  [{::keys [path]}]
  (-> *memory-repository
    (swap! (fn [memory-repository]
             (if (contains? memory-repository path)
               memory-repository
               (assoc memory-repository path (InMemoryRepository$Builder.)))))
    (get path)))

(defmethod db-uri->repository-builder "file"
  [{::keys [path]}]
  (-> (FileRepositoryBuilder.)
    (doto (.setGitDir (apply io/file (string/split path #"/"))))))

(def ^:dynamic *clock* (Clock/systemUTC))
(comment

  (defn tree-elements
    [^TreeWalk tw ^RevTree tree]
    (with-open [tree-walk (doto tw
                            (.reset tree))]
      (loop [vs []]
        (if (.next tree-walk)
          (recur (into vs
                   (map (fn [nth]
                          {::name      (.getNameString tree-walk)
                           ::path      (.getPathString tree-walk)
                           ::file-mode (.getFileMode tree-walk)
                           ::nth       nth
                           ::object-id (.getObjectId tree-walk nth)}))
                   (range (.getTreeCount tree-walk))))
          vs)))))


(defn insert!
  [{::keys [^ObjectInserter object-inserter
            ^ObjectReader object-reader
            path any-object-id trees]}]
  (loop [^AnyObjectId object-id any-object-id
         [^String name & path] (reverse (string/split path #"/"))
         mode FileMode/REGULAR_FILE]
    (let [next-tree (TreeFormatter.)]
      (when-not (seq path)
        (doseq [^RevTree tree trees]
          (with-open [tw (doto (TreeWalk. object-reader)
                           (.reset tree))]
            (when (.next tw)
              (dotimes [idx (.getTreeCount tw)]
                (.append next-tree
                  (.getNameString tw)
                  FileMode/TREE
                  (.getObjectId tw idx)))))))
      (.append next-tree name mode object-id)
      (if (seq path)
        (recur (.insert object-inserter next-tree)
          path FileMode/TREE)
        (.insert object-inserter next-tree)))))

(defn create-database
  [db-uri]
  (let [uri (parse-db-uri db-uri)
        repository (-> (db-uri->repository-builder uri)
                     (doto (.setInitialBranch "main"))
                     (.build)
                     (doto (.create true)))]
    (with-open [object-inserter (.newObjectInserter repository)]
      (let [^ByteArrayOutputStream created-at-blob (with-open [baos (ByteArrayOutputStream.)
                                                               w (io/writer baos)]
                                                     (json/write (str (Instant/now *clock*)) w)
                                                     baos)

            created-at-id (.insert object-inserter Constants/OBJ_BLOB
                            (.toByteArray created-at-blob))
            root-tree-id (insert! {::object-inserter object-inserter
                                   ::path            "db/created-at"
                                   ::any-object-id   created-at-id})
            commit (doto (CommitBuilder.)
                     (.setAuthor (PersonIdent. "chrondb" "chrondb@localhost" ^Long (inst-ms (Instant/now *clock*)) 0))
                     (.setCommitter (PersonIdent. "chrondb" "chrondb@localhost" ^Long (inst-ms (Instant/now *clock*)) 0))
                     (.setTreeId root-tree-id)
                     (.setMessage "init\n"))
            commit-id (.insert object-inserter commit)]
        (.flush object-inserter)
        (-> (.updateRef repository Constants/HEAD)
          (doto (.setExpectedOldObjectId (ObjectId/zeroId))
                (.setNewObjectId commit-id)
                (.setRefLogMessage "World" false))
          (.update))))
    repository))


(defn connect
  [db-uri]
  (let [uri (parse-db-uri db-uri)
        repository (-> (db-uri->repository-builder uri)
                     (.build))
        _ (when-not (.exists (.getDirectory repository))
            (throw (ex-info (str "Can't connect to " db-uri ": directory do not exists")
                     {:cognitect.anomalies/category :cognitect.anomalies/not-found
                      ::db-uri                      db-uri})))
        ;; Every git repo has a HEAD file
        ;; This file points to a ref file, that contains a sha of a commit
        ;; This commit has a reference to a tree
        branch (.resolve repository Constants/HEAD)]
    {::repository   repository
     ::value-reader (fn [^InputStream in]
                      (io/reader in))
     ::read-value   (fn [rdr]
                      (json/read rdr :key-fn keyword))
     ::value-writer (fn [^OutputStream out]
                      (io/writer out))
     ::write-value  (fn [x writer]
                      (json/write x writer))}))

(defn db
  [{::keys [^Repository repository]
    :as    chronn}]
  (let [commit (.parseCommit repository (.resolve repository Constants/HEAD))
        tree (.getTree commit)]
    (assoc chronn
      ::tree tree)))

(defn tree-elements
  [^TreeWalk tw ^RevTree tree]
  (with-open [tree-walk (doto tw
                          (.reset tree))]
    (loop [vs []]
      (if (.next tree-walk)
        (recur (into vs
                 (map (fn [nth]
                        {::name      (.getNameString tree-walk)
                         ::path      (.getPathString tree-walk)
                         ::file-mode (.getFileMode tree-walk)
                         ::nth       nth
                         ::object-id (.getObjectId tree-walk nth)}))
                 (range (.getTreeCount tree-walk))))
        vs))))


(defn repo->clj
  [{::keys [^Repository repository]}]
  (with-open [rw (RevWalk. repository)]
    (letfn [(any->clj [x]
              (try
                (commit->clj (.parseCommit rw x))
                (catch IncorrectObjectTypeException ex
                  (try
                    (tree->clj (.parseTree rw x))
                    (catch IncorrectObjectTypeException ex
                      (blob->clj (.lookupBlob rw x)))))))
            (blob->clj [^RevBlob blob]
              {:mode :blob
               :id   (second (string/split (str blob) #"\s"))})
            (commit->clj [^RevCommit commit]
              (let [commit (.parseCommit rw commit)]
                (merge {:mode :commit
                        :id   (second (string/split (str commit) #"\s"))}
                  (some-> (.getTree commit) tree->clj (->> (hash-map :tree)))
                  (when-let [parents (seq (map commit->clj (.getParents commit)))]
                    {:parents (vec parents)}))))
            (tree->clj [^RevTree tree]
              {:mode  :tree
               :nodes (vec (for [{::keys [path file-mode object-id]} (tree-elements (TreeWalk. repository) tree)]
                             {:path      path
                              :file-mode (str file-mode)
                              :node      (any->clj object-id)}))
               :id    (second (string/split (str tree) #"\s"))})]
      (let [commit (.parseCommit repository (.resolve repository Constants/HEAD))]
        (commit->clj commit)))))

(defn select-keys
  [{::keys [^Repository repository ^RevTree tree
            value-reader
            read-value]}
   ks]
  (let [reader (.newObjectReader repository)
        new-filter (PathFilterGroup/createFromStrings ^"[Ljava.lang.String;"
                     (into-array ks))
        tw (doto (TreeWalk. repository reader)
             (.setFilter new-filter)
             (.reset tree))]
    (loop []
      (when (and (.next tw)
              (.isSubtree tw))
        (.enterSubtree tw)
        (recur)))
    (into {}
      (map (fn [nth]
             (let [obj (.getObjectId tw nth)]
               (when-not (= (ObjectId/zeroId) obj)
                 (with-open [in ^AutoCloseable (value-reader (.openStream (.open repository obj)))]
                   (let [k (.getPathString tw)
                         v (read-value in)]
                     [k v]))))))
      (range (.getTreeCount tw)))))

(defn save
  [{::keys [^Repository repository value-writer write-value]
    :as    chronn} k v]

  (with-open [object-inserter (.newObjectInserter repository)
              rw (RevWalk. repository)
              object-reader (.newObjectReader repository)]
    (let [branch (.resolve repository Constants/HEAD)
          old-tree (.getTree (.parseCommit rw branch))
          ^ByteArrayOutputStream baos (with-open [baos (ByteArrayOutputStream.)
                                                  w ^AutoCloseable (value-writer baos)]
                                        (write-value v w)
                                        baos)
          blob (.toByteArray baos)
          blob-id (.insert object-inserter Constants/OBJ_BLOB blob)
          root-tree-id (insert! {::object-inserter object-inserter
                                 ::path            (str k)
                                 ::object-reader   object-reader
                                 ::trees           [old-tree]
                                 ::any-object-id   blob-id})
          commit (doto (CommitBuilder.)
                   (.setAuthor (PersonIdent. "chrondb" "chrondb@localhost" ^Long (inst-ms (Instant/now *clock*)) 0))
                   (.setCommitter (PersonIdent. "chrondb" "chrondb@localhost" ^Long (inst-ms (Instant/now *clock*)) 0))
                   (.setTreeId root-tree-id)
                   (.setParentId branch)
                   (.setMessage "Hello!\n"))
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
                       ::tree root-tree-id)}
          ;; RefUpdate$Result/NEW
          "NEW"
          {:db-after (assoc chronn
                       ::tree root-tree-id)}
          ;; RefUpdate$Result/FORCED
          "FORCED"
          {:db-after (assoc chronn
                       ::tree root-tree-id)})))))

(defmethod db-uri->repository-destroyer "file"
  [{::keys [db-uri]}]
  (try
    (let [chronn (connect db-uri)
          db (db chronn)
          created-at (get (select-keys db ["db/created-at"])
                       "db/created-at")]
      (when-not created-at
        (throw (ex-info (str "Repository " (pr-str db-uri) " isn't a valid chrondb.")
                 {:cognitect.anomalies/category :cognitect.anomalies/incorrect
                  ::db-uri                      db-uri})))
      (doseq [^File f (reverse (file-seq (.getDirectory ^Repository (::repository chronn))))]
        (.delete f))
      true)
    (catch Exception ex
      (let [{:cognitect.anomalies/keys [category]
             :as                       data} (ex-data ex)]
        (if (and (= (::db-uri data)
                   db-uri)
              (= :cognitect.anomalies/not-found category))
          false
          (throw ex))))))

(defn ls-files
  [{::keys [^RevTree tree ^Repository repository]}]
  (let [tree-walk (TreeWalk. repository)]
    (with-open [tree-walk (doto tree-walk
                            (.reset tree))]
      (loop [vs []]
        (if (.next tree-walk)
          (if (.isSubtree tree-walk)
            (do (.enterSubtree tree-walk)
                (recur vs))
            (recur (conj vs (.getPathString tree-walk))))
          vs)))))


