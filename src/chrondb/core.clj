(ns chrondb.core
  (:require [clojure.java.io :as io]
            [clojure.data.json :as json]
            [environ.core :refer [env]]
            [clucie.core :as index-core]
            [chrondb.search.index :as index])
  (:use clj-jgit.porcelain)
  (:gen-class))

(def my-struct
  [{:number "1" :title "Please Please Me"}
   {:number "2" :title "With the Beatles"}
   {:number "3" :title "A Hard Day's Night"}
   {:number "4" :title "Beatles for Sale"}
   {:number "5" :title "Help!"}])

(defn path->repo [path]
  (if (.isDirectory (io/file path))
    (load-repo path)
    (git-init :dir path)))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println ((my-struct 3) :number))
  (println (json/write-str my-struct))
  (let [chrondb-dir (.toString (java.util.UUID/randomUUID))
        chrondb-data-dir (or (env :data-dir) "data")
        chrondb-git-dir (str chrondb-data-dir "/" chrondb-dir)
        chrondb-repo (path->repo chrondb-git-dir)]

    ;; create file and add/commit
    (spit (str chrondb-git-dir "/flubber.json") (json/write-str my-struct))
    (git-commit chrondb-repo "Add file flubber.json"
                :all? true
                :no-verify? true
                :sign? false
                :commiter {:name "Avelino"
                           :email "a@b.ccc"})
    (println "repo status:" (git-status chrondb-repo))

    ;; index on lucene
    (index-core/add! index/store
                     my-struct
                     [:number :title]
                     index/analyzer)
    (println "search out:" 
             (index-core/phrase-search index/store
                                       {:title "beatles"}
                                       10
                                       index/analyzer
                                       0
                                       5))))
