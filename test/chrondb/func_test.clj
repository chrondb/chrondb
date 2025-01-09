(ns chrondb.func-test
  (:require [clojure.test :refer [deftest is testing]]
            [chrondb.func :as func])
  (:import (org.eclipse.jgit.lib Repository Constants)
           (org.eclipse.jgit.internal.storage.dfs InMemoryRepository)
           (org.eclipse.jgit.revwalk RevWalk)))

(defn get-head-tree
  [^Repository repo]
  (when-let [head (.resolve repo Constants/HEAD)]
    (with-open [walk (RevWalk. repo)]
      (let [commit (.parseCommit walk head)]
        (.getTree commit)))))

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

(deftest test-repository-operations
  (let [repo (func/create-repository "test-repo")]
    (testing "Repository creation"
      (is (not (nil? repo)))
      (is (instance? InMemoryRepository repo))
      (is (.exists repo)))

    (testing "Save and retrieve simple value"
      (let [test-key "test-key"
            test-value {:name "test" :value 42}]
        (func/save repo nil test-key test-value)
        (let [tree (get-head-tree repo)]
          (is (not (nil? tree)))
          (let [retrieved-value (func/get-value repo tree test-key)]
            (is (compare-values test-value retrieved-value))))))

    (testing "Save and retrieve multiple values"
      (let [values {"key1" {:a 1}
                   "key2" {:b 2}
                   "key3" {:c 3}}]
        (doseq [[k v] values]
          (let [tree (get-head-tree repo)]
            (func/save repo tree k v)))
        (let [final-tree (get-head-tree repo)]
          (doseq [[k v] values]
            (is (compare-values v (func/get-value repo final-tree k)))))))

    (testing "Update existing value"
      (let [test-key "update-key"
            initial-value {:status "initial"}
            updated-value {:status "updated"}]
        (func/save repo nil test-key initial-value)
        (let [tree (get-head-tree repo)]
          (func/save repo tree test-key updated-value)
          (let [final-tree (get-head-tree repo)]
            (is (compare-values updated-value (func/get-value repo final-tree test-key)))))))

    (testing "Get non-existent key"
      (let [tree (get-head-tree repo)]
        (is (nil? (func/get-value repo tree "non-existent-key")))))

    (testing "Save with nil tree"
      (let [test-key "nil-tree-key"
            test-value {:test "value"}]
        (func/save repo nil test-key test-value)
        (let [tree (get-head-tree repo)]
          (is (compare-values test-value (func/get-value repo tree test-key))))))

    (testing "Save with existing tree"
      (let [test-key "existing-tree-key"
            test-value {:test "value"}
            tree (get-head-tree repo)]
        (func/save repo tree test-key test-value)
        (let [new-tree (get-head-tree repo)]
          (is (compare-values test-value (func/get-value repo new-tree test-key))))))

    (testing "Get value with nil tree"
      (is (nil? (func/get-value repo nil "any-key"))))

    (testing "Save large data structure"
      (let [test-key "large-key"
            test-value {:data (repeat 1000 "test")}]
        (func/save repo nil test-key test-value)
        (let [tree (get-head-tree repo)]
          (is (compare-values test-value (func/get-value repo tree test-key)))))))) 