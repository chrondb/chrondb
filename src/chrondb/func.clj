(ns chrondb.func
  (:require [clojure.java.io :as io]
            [clojure.data.json :as json]
            [clj-compress.core :as c]
            [clj-jgit.porcelain :as jgit])
  (:import (org.eclipse.jgit.api Git)
           (java.io ByteArrayOutputStream)))

(def file-ext ".cdb")
(def back-to-repo "/../")
(def compressor-type "gz")

(defn path->repo
  [path]
  (if (.isDirectory (io/file path))
    (jgit/load-repo path)
    (jgit/git-init :dir path)))

(defn save
  [^Git repo key value
   & {:keys [msg commiter no-verify? sign?]
      :or   {msg        "saving content"
             commiter   {:name "chrondb-anonymous"}
             no-verify? true
             sign?      false}}]
  (let [repo-git-dir (-> repo .getRepository .getDirectory .toString)
        repo-dir (str repo-git-dir back-to-repo)
        data-filename (str key file-ext)
        data-filepath (str repo-dir data-filename)
        data->str (json/write-str value)]
    (c/compress-data (.getBytes data->str) data-filepath compressor-type)
    (jgit/git-commit repo (str msg data-filename)
                :all? true
                :no-verify? no-verify?
                :sign? sign?
                :commiter commiter)))

(defn find-by-key [^Git repo key]
  (let [repo-git-dir (-> repo .getRepository .getDirectory .toString)
        repo-dir (str repo-git-dir back-to-repo)
        data-filename (str key file-ext)
        data-filepath (str repo-dir data-filename)
        output (ByteArrayOutputStream.)]
    (c/decompress-data data-filepath output compressor-type)
    (json/read-str (.toString output))))