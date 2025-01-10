(ns chrondb.storage.memory-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [chrondb.storage.memory :as memory]
            [chrondb.storage.protocol :as storage]
            [clojure.data.json :as json])
  (:import (org.eclipse.jgit.lib Repository)))

(deftest test-memory-storage
  (testing "Memory Storage Operations"
    (let [storage (memory/create-memory-storage)]
      (testing "Storage creation"
        (is (instance? memory/MemoryStorage storage))
        (is (instance? Repository (:repository storage))))

      (testing "Save operation"
        (let [key "test-key"
              value {:name "John" :age 30}
              saved (storage/save storage key value)]
          (is (= value saved))))

      (testing "Get operation after save"
        (let [key "test-key"
              value {:name "John" :age 30}]
          (storage/save storage key value)
          (let [retrieved (storage/get-value storage key)]
            (is (= value retrieved)))))

      (testing "Get non-existent key"
        (is (nil? (storage/get-value storage "non-existent"))))

      (testing "Delete operation"
        (let [key "test-key"
              value {:name "John" :age 30}]
          (storage/save storage key value)
          (is (some? (storage/get-value storage key)))
          (storage/delete storage key)
          (is (nil? (storage/get-value storage key)))))

      (testing "Update existing key"
        (let [key "test-key"
              value1 {:name "John" :age 30}
              value2 {:name "John" :age 31}]
          (storage/save storage key value1)
          (storage/save storage key value2)
          (is (= value2 (storage/get-value storage key)))))

      (testing "Save multiple keys"
        (let [kvs {"key1" {:name "John"}
                  "key2" {:name "Jane"}
                  "key3" {:name "Bob"}}]
          (doseq [[k v] kvs]
            (storage/save storage k v))
          (doseq [[k v] kvs]
            (is (= v (storage/get-value storage k))))))

      (testing "Save with special characters in key"
        (let [key "test/key/with/slashes"
              value {:data "test"}]
          (storage/save storage key value)
          (is (= value (storage/get-value storage key)))))

      (testing "Save with nil value"
        (let [key "nil-key"]
          (storage/save storage key nil)
          (is (nil? (storage/get-value storage key)))))

      (testing "Save with complex nested structure"
        (let [key "complex-key"
              value {:name "Test"
                    :data {:nested {:deep {:array [1 2 3]
                                         :map {:a 1 :b 2}}}}}]
          (storage/save storage key value)
          (is (= value (storage/get-value storage key)))))

      (testing "Delete non-existent key"
        (is (nil? (storage/delete storage "non-existent-key")))))))) 