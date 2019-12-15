(ns chrondb.core
  (:require [clojure.java.io :as io]
            [clojure.data.json :as json]
            [clojure.pprint :as pp]
            [clucie.core :as core]
            [environ.core :refer [env]])
  (:use clj-jgit.porcelain)
  (:gen-class))

(def my-struct
  [{:number "1" :title "Please Please Me"}
   {:number "2" :title "With the Beatles"}
   {:number "3" :title "A Hard Day's Night"}
   {:number "4" :title "Beatles for Sale"}
   {:number "5" :title "Help!"}])

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  ;; (.mkdir (io/file "lerolero"))
  (pp/pprint ((my-struct 3) :number))
  (pp/pprint (json/write-str my-struct))
  (let [chrondb-dir (.toString (java.util.UUID/randomUUID))
        chrondb-data-dir (or (env :data-dir) "data")
        chrondb-git-dir (str chrondb-data-dir "/" chrondb-dir)]
    (def my-repo (git-init :dir chrondb-git-dir))
    (pp/pprint chrondb-git-dir)
    (spit (str chrondb-git-dir "/flubber.json") (json/write-str my-struct))
    (git-add my-repo "flubber.json")
    (pp/pprint (git-status my-repo))
    (git-commit my-repo "Add file flubber.json" :sign? false :commiter {:name "Avelino" :email "a@b.ccc"})
    (pp/pprint (git-status my-repo))))
