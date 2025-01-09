(ns chrondb.core
  (:require [chrondb.func :as func]
            [chrondb.search.index :as index]
            [clucie.core :as index-core]
            [talltale.core :as faker]
            [chrondb.config :as config])
  (:gen-class))

(def test-search-username ((faker/person :en) :username))
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


(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "args:" args)
  (let [chrondb-local-repo (func/create-repository :branch-name config/chrondb-local-repo-branch)
        index-store (index/store :type "memory")
        my-key "my-key"]

    ;; chrondb save
    (func/save chrondb-local-repo my-key chrondb-struct-value-merged
               :branch-name "lerolero2")
    ;; chrondb find by key
    (println "find-by-key:" (func/find-by-key chrondb-local-repo my-key))

    ;; lucene index test
    (index-core/add! index-store chrondb-struct-value-merged [:age :username] index/analyzer)
    (println
     "search out:"
     (index-core/phrase-search index-store {:username test-search-username} 10 index/analyzer 0 5))))
