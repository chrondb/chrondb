(ns chrondb.config
  (:require [environ.core :refer [env]]))


(def default-branch-name "main")
(def file-ext ".cdb")
(def back-to-repo "/../")
(def compressor-type "gz")

;; chrondb configs
(def chrondb-local-dir (or (env :chrondb-name) (.toString (java.util.UUID/randomUUID))))
(def chrondb-local-data-dir (or (env :chrondb-local-data-dir) "data"))
(def chrondb-local-git-dir (str chrondb-local-data-dir "/" chrondb-local-dir "/"))
(def chrondb-local-repo-branch (or (env :chrondb-local-repo-branch) default-branch-name))
(def chrondb-remote-repo (or (env :chrondb-remote-repo-branch) nil))
(def chrondb-remote-repo-branch (or (env :chrondb-remote-repo-branch) default-branch-name))
