(ns chrondb.core
  (:require [clojure.java.io :as io]
            [clojure.pprint :as pp]
            [environ.core :refer [env]])
  (:use clj-jgit.porcelain)
  (:gen-class))


(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  ;; (.mkdir (io/file "lerolero"))
  (let [chrondb-dir (.toString (java.util.UUID/randomUUID))
        chrondb-data-dir (or (env :data-dir) "data")
        chrondb-git-dir (str chrondb-data-dir "/" chrondb-dir)]
    (git-init :dir chrondb-git-dir)
    (pp/pprint chrondb-git-dir)))
