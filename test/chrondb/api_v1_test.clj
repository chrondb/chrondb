(ns chrondb.api-v1-test
  (:require [clojure.test :refer [deftest is testing]]
            [chrondb.api-v1 :as api-v1])
  (:import (java.time Clock)
           (org.eclipse.jgit.lib AnyObjectId)
           (java.io File)))

(deftest type-predicates
  (testing "any-object-id?"
    (is (not (api-v1/any-object-id? nil)))
    (is (not (api-v1/any-object-id? "not-an-object-id")))
    (is (api-v1/any-object-id? (proxy [AnyObjectId] []))))
  
  (testing "file?"
    (is (not (api-v1/file? nil)))
    (is (not (api-v1/file? "not-a-file")))
    (is (api-v1/file? (File. "test-file")))))

(deftest uri-parsing
  (testing "parse-db-uri"
    (testing "valid URIs"
      (is (= #:chrondb.api-v1{:scheme "mem"
                             :db-uri "chrondb:mem://test"
                             :path "test"}
             (api-v1/parse-db-uri "chrondb:mem://test")))
      
      (is (= #:chrondb.api-v1{:scheme "file"
                             :db-uri "chrondb:file://test/path"
                             :path "test/path"}
             (api-v1/parse-db-uri "chrondb:file://test/path"))))
    
    (testing "invalid URIs"
      (is (thrown-with-msg? clojure.lang.ExceptionInfo
                           #"Only uri's starting with 'chrondb' are supported"
                           (api-v1/parse-db-uri "invalid:mem://test"))))))

(deftest repository-management
  (testing "Memory repository management"
    (let [db-uri "chrondb:mem://test-repo"]
      (testing "Create and connect"
        (api-v1/delete-database db-uri)
        (let [repo (api-v1/create-database db-uri)
              conn (api-v1/connect db-uri)]
          (is (some? repo))
          (is (map? conn))
          (is (contains? conn :chrondb.api-v1/repository))
          (is (fn? (:chrondb.api-v1/value-reader conn)))
          (is (fn? (:chrondb.api-v1/read-value conn)))
          (is (fn? (:chrondb.api-v1/value-writer conn)))
          (is (fn? (:chrondb.api-v1/write-value conn)))))
      
      (testing "Unsupported repository type"
        (is (thrown-with-msg? clojure.lang.ExceptionInfo
                             #"Only in-memory repositories are supported"
                             (api-v1/connect "chrondb:file://test")))))))

(deftest tree-structure-initial
  (binding [api-v1/*clock* (proxy [Clock] []
                            (instant []
                              (.toInstant #inst"2000")))]
    (let [chronn (-> "chrondb:mem://test-initial"
                  (doto api-v1/delete-database
                        api-v1/create-database)
                  api-v1/connect)
          repo-state (api-v1/repo->clj chronn)]
      (is (= "ac9ca5ace6edc38c42358de2ddf86a63e01d73c7"
             (-> repo-state :tree :nodes first :node :nodes first :node :id))))))

(deftest tree-structure-one-commit
  (binding [api-v1/*clock* (proxy [Clock] []
                            (instant []
                              (.toInstant #inst"2000")))]
    (let [chronn (-> "chrondb:mem://test-one-commit"
                  (doto api-v1/delete-database
                        api-v1/create-database)
                  api-v1/connect)]
      (api-v1/save chronn "n" 0)
      (let [repo-state (api-v1/repo->clj chronn)]
        (is (= "ac9ca5ace6edc38c42358de2ddf86a63e01d73c7"
               (-> repo-state :tree :nodes first :node :nodes first :node :id)))
        (is (= "c227083464fb9af8955c90d2924774ee50abb547"
               (-> repo-state :tree :nodes second :node :id)))))))

(deftest tree-structure-two-commit
  (binding [api-v1/*clock* (proxy [Clock] []
                            (instant []
                              (.toInstant #inst"2000")))]
    (let [chronn (-> "chrondb:mem://test-two-commit"
                  (doto api-v1/delete-database
                        api-v1/create-database)
                  api-v1/connect)]
      (api-v1/save chronn "n" 0)
      (api-v1/save chronn "n" 1)
      (let [repo-state (api-v1/repo->clj chronn)]
        (is (= "ac9ca5ace6edc38c42358de2ddf86a63e01d73c7"
               (-> repo-state :tree :nodes first :node :nodes first :node :id)))
        (is (= "56a6051ca2b02b04ef92d5150c9ef600403cb1de"
               (-> repo-state :tree :nodes second :node :id)))))))

(deftest basic-operations
  (binding [api-v1/*clock* (proxy [Clock] []
                            (instant []
                              (.toInstant #inst"2000")))]
    (let [chronn (-> "chrondb:mem://test-basic"
                  (doto api-v1/delete-database
                        api-v1/create-database)
                  api-v1/connect)]
      (testing "Initial state"
        (is (= {"db/created-at" "2000-01-01T00:00:00Z"}
              (api-v1/select-keys (api-v1/db chronn)
                ["db/created-at"]))))
      
      (testing "Save and retrieve"
        (api-v1/save chronn "test-key" 42)
        (is (= {"test-key" 42}
              (api-v1/select-keys (api-v1/db chronn)
                ["test-key"]))))
      
      (testing "Update value"
        (api-v1/save chronn "test-key" 43)
        (is (= {"test-key" 43}
              (api-v1/select-keys (api-v1/db chronn)
                ["test-key"]))))
      
      (testing "Multiple keys"
        (api-v1/save chronn "another-key" "value")
        (is (= {"test-key" 43
                "another-key" "value"}
              (api-v1/select-keys (api-v1/db chronn)
                ["test-key" "another-key"]))))
      
      (testing "Created-at remains unchanged"
        (is (= {"db/created-at" "2000-01-01T00:00:00Z"}
              (api-v1/select-keys (api-v1/db chronn)
                ["db/created-at"]))))
      
      (testing "Select non-existent keys"
        (is (= {}
              (api-v1/select-keys (api-v1/db chronn)
                ["non-existent-key"]))))
      
      (testing "Select tree paths"
        (is (= {}
              (api-v1/select-keys (api-v1/db chronn)
                ["db"]))))
      
      (testing "Select with empty key list"
        (is (= {}
              (api-v1/select-keys (api-v1/db chronn)
                [])))))))

(deftest repo-clj-conversion
  (binding [api-v1/*clock* (proxy [Clock] []
                            (instant []
                              (.toInstant #inst"2000")))]
    (let [chronn (-> "chrondb:mem://test-repo-clj"
                  (doto api-v1/delete-database
                        api-v1/create-database)
                  api-v1/connect)]
      
      (testing "Initial repository state"
        (let [repo-state (api-v1/repo->clj chronn)]
          (is (= :commit (:mode repo-state)))
          (is (string? (:id repo-state)))
          (is (= :tree (-> repo-state :tree :mode)))
          (is (vector? (-> repo-state :tree :nodes)))))
      
      (testing "After adding data"
        (api-v1/save chronn "test-key" {:data "test"})
        (let [repo-state (api-v1/repo->clj chronn)]
          (is (= :commit (:mode repo-state)))
          (is (string? (:id repo-state)))
          (is (= :tree (-> repo-state :tree :mode)))
          (is (vector? (-> repo-state :tree :nodes)))
          (is (some #(= "test-key" (:path %))
                   (-> repo-state :tree :nodes))))))))