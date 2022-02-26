(ns chrondb.api-v1-test
  (:require [chrondb.api-v1 :as api-v1]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.java.shell :as sh]
            [clojure.pprint :as pp]
            [clojure.test :refer [deftest is]])
  (:import (java.io File)
           (java.time Clock)))

(deftest tree-structure-initial
  (doseq [^File f (reverse (file-seq (io/file "data")))]
    (.delete f))
  (let [target-tree {:mode :commit,
                     :id   "8032734087759f0f070110508bfb5121ec0953ea",
                     :tree {:mode  :tree
                            :nodes [{:path      "db",
                                     :file-mode "40000",
                                     :node      {:mode  :tree,
                                                 :nodes [{:path      "created-at",
                                                          :file-mode "100644",
                                                          :node      {:mode :blob
                                                                      :id   "ac9ca5ace6edc38c42358de2ddf86a63e01d73c7"}}]
                                                 :id    "0c1f142900173cf36350f9d2b72a5ad4f42244cf"}}]
                            :id    "a94d21dae8119225c328cf695d146e1bc508a3b4"}}]
    (binding [api-v1/*clock* (proxy [Clock] []
                               (instant []
                                 (.toInstant #inst"2000")))]
      (let [chronn (-> "chrondb:file://data/tree-structure-api"
                     (doto api-v1/delete-database
                           api-v1/create-database)
                     api-v1/connect)]
        (is (= target-tree

              (-> (api-v1/repo->clj chronn)
                (doto pp/pprint)))))
      (let [dir (io/file "data" "tree-structure-cli")]
        (binding [sh/*sh-dir* dir
                  sh/*sh-env* {"GIT_AUTHOR_NAME"     "chrondb"
                               "GIT_AUTHOR_EMAIL"    "chrondb@localhost"
                               "GIT_AUTHOR_DATE"     "946684800 +0000"
                               "GIT_COMMITTER_NAME"  "chrondb"
                               "GIT_COMMITTER_EMAIL" "chrondb@localhost"
                               "GIT_COMMITTER_DATE"  "946684800 +0000"}]
          (.mkdirs dir)
          (prn (sh/sh "git" "init" "--initial-branch=main" "."))
          (prn (sh/sh "mkdir" "db"))
          (spit (io/file dir "db" "created-at")
            (json/write-str (str (.toInstant #inst"2000"))))
          (prn (sh/sh "git" "add" "db/created-at"))
          (prn (sh/sh "git" "commit" "-aminit")))
        (sh/sh "git" "clone" "--bare" "tree-structure-cli" "tree-structure-cli-bare"
          :dir (io/file "data"))
        (is (= target-tree
              (-> (api-v1/repo->clj (api-v1/connect "chrondb:file://data/tree-structure-cli-bare"))
                (doto pp/pprint))))))))


(deftest tree-structure-one-commit
  (doseq [^File f (reverse (file-seq (io/file "data")))]
    (.delete f))
  (let [target-tree {:mode    :commit,
                     :id      "2bb88ab180c2007177b7b177ce443bff1dfb4b6b",
                     :tree    {:mode  :tree
                               :id    "da8827b2ac89361970b216f063281dcd36754436"
                               :nodes [{:path      "db",
                                        :file-mode "40000",
                                        :node      {:mode  :tree,
                                                    :nodes [{:path      "created-at",
                                                             :file-mode "100644",
                                                             :node
                                                             {:mode :blob
                                                              :id   "ac9ca5ace6edc38c42358de2ddf86a63e01d73c7"}}],
                                                    :id    "0c1f142900173cf36350f9d2b72a5ad4f42244cf"}}
                                       {:path      "n"
                                        :file-mode "100644"
                                        :node      {:mode :blob
                                                    :id   "c227083464fb9af8955c90d2924774ee50abb547"}}],},
                     :parents [{:mode :commit,
                                :id   "8032734087759f0f070110508bfb5121ec0953ea",
                                :tree {:mode :tree}}]}]
    (binding [api-v1/*clock* (proxy [Clock] []
                               (instant []
                                 (.toInstant #inst"2000")))]
      (let [chronn (-> "chrondb:file://data/tree-structure-one-commit"
                     (doto api-v1/delete-database
                           api-v1/create-database)
                     api-v1/connect)]
        (api-v1/save chronn "n" 0)
        (is (= target-tree

              (-> (api-v1/repo->clj chronn)
                (doto pp/pprint)))))
      (let [dir (io/file "data" "tree-structure-one-commit-cli")]
        (binding [sh/*sh-dir* dir
                  sh/*sh-env* {"GIT_AUTHOR_NAME"     "chrondb"
                               "GIT_AUTHOR_EMAIL"    "chrondb@localhost"
                               "GIT_AUTHOR_DATE"     "946684800 +0000"
                               "GIT_COMMITTER_NAME"  "chrondb"
                               "GIT_COMMITTER_EMAIL" "chrondb@localhost"
                               "GIT_COMMITTER_DATE"  "946684800 +0000"}]
          (.mkdirs dir)
          (prn (sh/sh "git" "init" "--initial-branch=main" "."))
          (prn (sh/sh "mkdir" "db"))
          (spit (io/file dir "db" "created-at")
            (json/write-str (str (.toInstant #inst"2000"))))
          (prn (sh/sh "git" "add" "db/created-at"))
          (prn (sh/sh "git" "commit" "-aminit"))
          (spit (io/file dir "n")
            (json/write-str 0))
          (prn (sh/sh "git" "add" "n"))
          (prn (sh/sh "git" "commit" "-amHello!")))
        (sh/sh "git" "clone" "--bare" "tree-structure-one-commit-cli" "tree-structure-one-commit-cli-bare"
          :dir (io/file "data"))
        (is (= target-tree
              (-> (api-v1/repo->clj (api-v1/connect "chrondb:file://data/tree-structure-one-commit-cli-bare"))
                (doto pp/pprint))))))))


(deftest tree-structure-two-commit
  (doseq [^File f (reverse (file-seq (io/file "data")))]
    (.delete f))
  (let [target-tree {:mode :commit,
                     :id   "b27a5ddb587294b578136293087cefda502bc4dc",
                     :tree
                     {:mode :tree,
                      :nodes
                      [{:path      "db",
                        :file-mode "40000",
                        :node
                        {:mode :tree,
                         :nodes
                         [{:path      "created-at",
                           :file-mode "100644",
                           :node
                           {:mode :blob, :id "ac9ca5ace6edc38c42358de2ddf86a63e01d73c7"}}],
                         :id   "0c1f142900173cf36350f9d2b72a5ad4f42244cf"}}
                       {:path      "n",
                        :file-mode "100644",
                        :node
                        {:mode :blob, :id "56a6051ca2b02b04ef92d5150c9ef600403cb1de"}}],
                      :id   "b2fafedd676bed8b9aa0ea018f7e2e58ebb16222"},
                     :parents
                     [{:mode :commit,
                       :id   "2bb88ab180c2007177b7b177ce443bff1dfb4b6b",
                       :tree {:mode :tree}}]}]
    (binding [api-v1/*clock* (proxy [Clock] []
                               (instant []
                                 (.toInstant #inst"2000")))]
      (let [chronn (-> "chrondb:file://data/tree-structure-two-commit"
                     (doto api-v1/delete-database
                           api-v1/create-database)
                     api-v1/connect)]
        (api-v1/save chronn "n" 0)
        (api-v1/save chronn "n" 1)
        (is (= target-tree

              (-> (api-v1/repo->clj chronn)
                (doto pp/pprint)))))
      (let [dir (io/file "data" "tree-structure-two-commit-cli")]
        (binding [sh/*sh-dir* dir
                  sh/*sh-env* {"GIT_AUTHOR_NAME"     "chrondb"
                               "GIT_AUTHOR_EMAIL"    "chrondb@localhost"
                               "GIT_AUTHOR_DATE"     "946684800 +0000"
                               "GIT_COMMITTER_NAME"  "chrondb"
                               "GIT_COMMITTER_EMAIL" "chrondb@localhost"
                               "GIT_COMMITTER_DATE"  "946684800 +0000"}]
          (.mkdirs dir)
          (prn (sh/sh "git" "init" "--initial-branch=main" "."))
          (prn (sh/sh "mkdir" "db"))
          (spit (io/file dir "db" "created-at")
            (json/write-str (str (.toInstant #inst"2000"))))
          (prn (sh/sh "git" "add" "db/created-at"))
          (prn (sh/sh "git" "commit" "-aminit"))
          (spit (io/file dir "n")
            (json/write-str 0))
          (prn (sh/sh "git" "add" "n"))
          (prn (sh/sh "git" "commit" "-amHello!"))
          (spit (io/file dir "n")
            (json/write-str 1))
          (prn (sh/sh "git" "add" "n"))
          (prn (sh/sh "git" "commit" "-amHello!")))
        (sh/sh "git" "clone" "--bare" "tree-structure-two-commit-cli" "tree-structure-two-commit-cli-bare"
          :dir (io/file "data"))
        (is (= target-tree
              (-> (api-v1/repo->clj (api-v1/connect "chrondb:file://data/tree-structure-two-commit-cli-bare"))
                (doto pp/pprint))))))))





(deftest hello
  #_(doseq [^File f (reverse (file-seq (io/file "data")))]
      (.delete f))
  (binding [api-v1/*clock* (proxy [Clock] []
                             (instant []
                               (.toInstant #inst"2000")))]
    (let [chronn (-> "chrondb:file://data/counter-v1"
                   #_(doto api-v1/delete-database
                       api-v1/create-database)
                   api-v1/connect)]
      (is (= {"db/created-at" "2000-01-01T00:00:00Z"}
            (-> (api-v1/select-keys (api-v1/db chronn)
                  ["db/created-at"])
              (doto pp/pprint))))
      (is (= {"n" 42}
            (-> (api-v1/save chronn "n" 42)
              :db-after
              (api-v1/select-keys ["n"])
              (doto pp/pprint))))
      (is (= {"db/created-at" "2000-01-01T00:00:00Z"}
            (-> (api-v1/select-keys (api-v1/db chronn)
                  ["db/created-at"])
              (doto pp/pprint))))
      (is (= {"n" 42}
            (-> (api-v1/select-keys (api-v1/db chronn)
                  ["n"])
              (doto pp/pprint)))))))