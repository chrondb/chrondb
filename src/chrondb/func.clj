(ns chrondb.func
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clj-compress.core :as c]
            [clj-jgit.porcelain :as jgit]
            [chrondb.config :as config])
  (:import (java.io ByteArrayOutputStream)
           (org.eclipse.jgit.api Git)))

(defn branch-current?
  "compares the branch name"
  [repo branch-name]
  (if (= (jgit/git-branch-current repo) branch-name)
    true false))

(defn branch-checkout
  "branch handling via checkout, if the branch does not exist it will be created"
  [repo branch-name]
  (cond
    (.contains (jgit/git-branch-list repo) branch-name)
    (jgit/git-branch-create repo branch-name :force? true))
  (cond
    (branch-current? repo branch-name)
    (jgit/git-checkout repo :name branch-name)))

(defn path->load-repo
  "load existing repository"
  [path & {:keys [branch-name]
           :or {branch-name config/default-branch-name}}]
  (let [repo (jgit/load-repo path)]
    (branch-checkout repo branch-name)
    repo))

(defn path->repo
  "transform path in git repository, if it doesn't exist it will be created"
  [path & {:keys [branch-name]
           :or   {branch-name config/default-branch-name}}]
  (if (.isDirectory (io/file path))
    (path->load-repo path :branch-name branch-name)
    (jgit/git-init :initial-branch branch-name :dir path)))

(defn save
  "save (insert/update) information in the repository"
  [^Git repo key value
   & {:keys [branch-name msg commiter no-verify? sign? all?]
      :or   {branch-name config/default-branch-name
             msg         "saving content"
             commiter    {:name "chrondb-anonymous"}
             no-verify?  true
             sign?       false
             all?        true}}]
  (let [repo-git-dir  (-> repo .getRepository .getDirectory .toString)
        repo-dir      (str repo-git-dir config/back-to-repo)
        data-filename (str key config/file-ext)
        data-filepath (str repo-dir data-filename)
        data->str     (json/write-str value)]
    ;; if it doesn't exist it will be created and cehckout
    (branch-checkout repo branch-name)
    ;; compress data
    (c/compress-data (.getBytes data->str) data-filepath config/compressor-type)
    ;; git add and commit
    (jgit/git-add repo data-filename)
    (jgit/git-commit repo (str msg ": " data-filename)
                     :all? all?
                     :no-verify? no-verify?
                     :sign? sign?
                     :commiter commiter)))

(defn find-by-key
  "find by key registered in the git repository"
  [^Git repo key]
  (let [repo-git-dir (-> repo .getRepository .getDirectory .toString)
        repo-dir (str repo-git-dir config/back-to-repo)
        data-filename (str key config/file-ext)
        data-filepath (str repo-dir data-filename)
        output (ByteArrayOutputStream.)]
    (c/decompress-data data-filepath output config/compressor-type)
    (json/read-str (.toString output))))
