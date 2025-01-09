(ns chrondb.core
  (:require [chrondb.func :as func]
            [chrondb.search.index :as index]
            [clucie.core :as index-core]
            [talltale.core :as faker])
  (:import (org.eclipse.jgit.lib Repository)
           (org.eclipse.jgit.revwalk RevTree)))

(def test-search-username ((faker/person :en) :username))

(defn create-repository
  [path]
  (func/create-repository path))

(defn save
  [^Repository repository ^RevTree tree k v]
  (func/save repository tree k v))

(defn get-value
  [^Repository repository ^RevTree tree k]
  (func/get-value repository tree k))

(defn search
  [index-store _]
  (let [search-result (index-core/phrase-search index-store {:username test-search-username} 10 index/analyzer 0 5)]
    (str "search out:" search-result)))
