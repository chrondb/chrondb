(ns chrondb.storage.git-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [chrondb.storage.git :as git]
            [chrondb.storage.protocol :as protocol]
            [clojure.java.io :as io])
  (:import [org.eclipse.jgit.api Git]))

(def test-repo-path "test-repo")

(defn clean-test-repo [f]
  (let [repo-dir (io/file test-repo-path)]
    (when (.exists repo-dir)
      (doseq [file (reverse (file-seq repo-dir))]
        (.delete file))))
  (f))

(use-fixtures :each clean-test-repo)

(defn count-commits [git]
  (count (iterator-seq (.iterator (.call (.log git))))))

(defn get-last-commit-message [git]
  (.getFullMessage (first (iterator-seq (.iterator (.call (.log git)))))))

(deftest test-create-repository
  (testing "Creating a new Git repository"
    (let [repo (git/create-repository test-repo-path)
          git-api (Git. repo)]
      (is (instance? Git git-api))
      (is (.exists (io/file test-repo-path)))
      (is (.exists (io/file (str test-repo-path "/.git"))))
      (is (= 1 (count-commits git-api)))
      (is (= "Initial empty commit" (get-last-commit-message git-api))))))

(deftest test-git-storage
  (testing "Git storage operations"
    (let [storage (git/create-git-storage test-repo-path)
          doc {:id "test:1" :name "Test Doc" :value 42}]

      (testing "Save document"
        (is (= doc (protocol/save-document storage doc)))
        (let [git-api (Git. (:repository storage))]
          (is (= 2 (count-commits git-api)))
          (is (= "Save document" (get-last-commit-message git-api)))))

      (testing "Get document"
        (is (= doc (protocol/get-document storage "test:1")))
        (is (nil? (protocol/get-document storage "nonexistent"))))

      (testing "Delete document"
        (is (true? (protocol/delete-document storage "test:1")))
        (is (nil? (protocol/get-document storage "test:1"))))

      (testing "Close storage"
        (let [closed-storage (protocol/close storage)]
          (is (nil? (:repository closed-storage))))))))

(deftest test-git-storage-error-cases
  (testing "Git storage error handling"
    (let [storage (git/create-git-storage test-repo-path)]

      (testing "Delete document from empty repository"
        (is (false? (protocol/delete-document storage "nonexistent"))))

      (testing "Save invalid document"
        (is (thrown? Exception (protocol/save-document storage nil))))

      (testing "Close already closed storage"
        (let [closed-storage (protocol/close storage)]
          (is (thrown? Exception (protocol/save-document closed-storage {:id "test:1"}))))))))