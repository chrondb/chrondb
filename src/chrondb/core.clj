(ns chrondb.core
  (:require [environ.core :refer [env]]
            [clucie.core :as index-core]
            [chrondb.search.index :as index]
            [chrondb.func :as func])
  (:gen-class))

(def chrondb-struct-value
  [{:number "1" :title "Please Please Me"}
   {:number "2" :title "With the Beatles"}
   {:number "3" :title "A Hard Day's Night"}
   {:number "4" :title "Beatles for Sale"}
   {:number "5" :title "Help!"}])

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (let [chrondb-dir (.toString (java.util.UUID/randomUUID))
        chrondb-data-dir (or (env :data-dir) "data")
        chrondb-git-dir (str chrondb-data-dir "/" chrondb-dir "/")
        chrondb-repo (func/path->repo chrondb-git-dir)]

    ;; chrondb save
    (func/save chrondb-repo 1 chrondb-struct-value)
    ;; chrondb find by key
    (println "find-by-key:" (func/find-by-key chrondb-repo 1))

    ;; index on lucene
    (index-core/add! index/store
                     chrondb-struct-value
                     [:number :title]
                     index/analyzer)
    (println "search out:"
             (index-core/phrase-search index/store
                                       {:title "beatles"}
                                       10
                                       index/analyzer
                                       0
                                       5))))