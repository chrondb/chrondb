(ns chrondb.core
  (:require [chrondb.func :as func]
            [chrondb.search.index :as index]
            [clucie.core :as index-core]
            [talltale.core :as faker])
  (:import (org.eclipse.jgit.lib Repository Constants)
           (org.eclipse.jgit.revwalk RevTree RevWalk))
  (:gen-class))

(def test-search-username ((faker/person :en) :username))

(defn create-repository
  [path]
  (func/create-repository path))

(defn save
  [^Repository repository ^RevTree tree k v]
  (func/save repository tree k v))

(defn get-value
  [^Repository repository ^RevTree tree k]
  (func/get-value repository tree k))

(defn search
  [index-store _]
  (try
    (let [search-result (index-core/phrase-search index-store {:username test-search-username} 10 index/analyzer 0 5)]
      (str "search out:" search-result))
    (catch Exception _
      "search out:[]")))

(def chrondb-struct-value
  [{:username ((faker/person :en) :username)
    :age      ((faker/person :en) :age)}
   {:username ((faker/person :en) :username)
    :age      ((faker/person :en) :age)}
   {:username ((faker/person :en) :username)
    :age      ((faker/person :en) :age)}
   {:username ((faker/person :en) :username)
    :age      ((faker/person :en) :age)}
   {:username ((faker/person :en) :username)
    :age      ((faker/person :en) :age)}])

(def chrondb-struct-value-merged
  (conj chrondb-struct-value
        {:username test-search-username
         :age      ((faker/person :en) :age)}))

(defn get-head-tree
  [^Repository repository]
  (when-let [head (.resolve repository Constants/HEAD)]
    (with-open [walk (RevWalk. repository)]
      (let [commit (.parseCommit walk head)]
        (.getTree commit)))))

(defn -main
  "Main entry point for the application."
  [& args]
  (println "args:" args)
  (let [repository (create-repository "test-repo")
        index-store (index/store :type "memory")
        my-key "my-key"]

    ;; chrondb save
    (save repository nil my-key chrondb-struct-value-merged)
    
    ;; chrondb find by key
    (let [tree (get-head-tree repository)]
      (println "find-by-key:" (get-value repository tree my-key)))

    ;; lucene index test
    (index-core/add! index-store chrondb-struct-value-merged [:age :username] index/analyzer)
    (println
     "search out:"
     (index-core/phrase-search index-store {:username test-search-username} 10 index/analyzer 0 5))))