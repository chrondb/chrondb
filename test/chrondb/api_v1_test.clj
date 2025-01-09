(ns chrondb.api-v1-test
  (:require [clojure.test :refer [deftest is testing]]
            [chrondb.api-v1 :as api-v1])
  (:import (java.time Clock)))

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
                ["db/created-at"])))))))