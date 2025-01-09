(ns chrondb.config
  (:require [environ.core :refer [env]]))

(def chrondb-local-git-dir
  (or (env :chrondb-local-git-dir)
      ".chrondb"))

(def chrondb-local-repo-branch
  (or (env :chrondb-local-repo-branch)
      "main"))
