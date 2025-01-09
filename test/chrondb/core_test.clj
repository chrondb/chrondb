(ns chrondb.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [chrondb.core :as core]
            [chrondb.search.index :as index]
            [clucie.core :as index-core]))

(defn compare-values [expected actual]
  (cond
    (map? expected)
    (every? (fn [[k v]]
              (= v (get actual (name k))))
            expected)
    
    (sequential? expected)
    (and (sequential? actual)
         (= (count expected) (count actual))
         (every? true? (map compare-values expected actual)))
    
    :else
    (= expected actual)))

(deftest test-core-operations
  (testing "Core repository operations"
    (let [repo (core/create-repository "test-repo")]
      (testing "Repository creation"
        (is (not (nil? repo)))
        (is (.exists repo)))

      (testing "Save and retrieve operations"
        (let [test-key "test-key"
              test-value {:username "test-user" :age 25}]
          (core/save repo nil test-key test-value)
          (let [tree (core/get-head-tree repo)]
            (is (not (nil? tree)))
            (let [retrieved-value (core/get-value repo tree test-key)]
              (is (compare-values test-value retrieved-value))))))

      (testing "Get head tree"
        (is (not (nil? (core/get-head-tree repo))))
        ;; Test with empty repository
        (let [empty-repo (core/create-repository "empty-repo")]
          (is (nil? (core/get-head-tree empty-repo))))))))

(deftest test-search-operations
  (testing "Search functionality"
    (let [index-store (index/store :type "memory")
          test-data core/chrondb-struct-value-merged]
      
      (testing "Search with empty index"
        (try
          (is (= "search out:[]" (core/search index-store nil)))
          (catch Exception _
            (is true "Expected exception for empty index"))))
      
      (testing "Search with populated index"
        (index-core/add! index-store test-data [:age :username] index/analyzer)
        (let [search-result (core/search index-store nil)]
          (is (string? search-result))
          (is (not= "search out:[]" search-result)))))))

(deftest test-main-function
  (testing "Main function execution"
    (let [args ["test-arg"]]
      (is (nil? (core/-main args))))))

(deftest test-data-structures
  (testing "Test data structure validation"
    (testing "chrondb-struct-value"
      (is (vector? core/chrondb-struct-value))
      (is (= 5 (count core/chrondb-struct-value)))
      (doseq [item core/chrondb-struct-value]
        (is (map? item))
        (is (contains? item :username))
        (is (contains? item :age))))
    
    (testing "chrondb-struct-value-merged"
      (is (vector? core/chrondb-struct-value-merged))
      (is (= 6 (count core/chrondb-struct-value-merged)))
      (is (some #(= (:username %) core/test-search-username) 
                core/chrondb-struct-value-merged))))) 