(ns chrondb.core
  (:require [chrondb.func :as func]
            [chrondb.search.index :as index]
            [clucie.core :as index-core]
            [environ.core :refer [env]])
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
  (println "args:" args)
  (let [chrondb-dir (.toString (java.util.UUID/randomUUID))
        chrondb-data-dir (or (env :data-dir) "data")
        chrondb-git-dir (str chrondb-data-dir "/" chrondb-dir "/")
        chrondb-repo (func/path->repo chrondb-git-dir)]

    ;; chrondb save
    (func/save chrondb-repo 1 chrondb-struct-value)
    ;; chrondb find by key
    (println "find-by-key:" (func/find-by-key chrondb-repo 1))

    ;; lucene store
    (def index-store (index/store :type "memory"))

    ;; lucene index test
    (index-core/add!
     index-store
     chrondb-struct-value
     [:number :title]
     index/analyzer)
    (println "search out:"
             (index-core/phrase-search
              index-store
              {:title "beatles"}
              10
              index/analyzer
              0
              5))))
