(ns chrondb.storage.memory-test
  (:require [chrondb.storage.memory :as memory]
            [chrondb.storage.protocol :as protocol]
            [clojure.test :refer :all]))

(deftest test-memory-storage
  (let [storage (memory/create-memory-storage)]
    (testing "Save and get document"
      (let [doc {:id "test:1" :name "Test Doc" :value 42}]
        (protocol/save-document storage doc)
        (is (= doc (protocol/get-document storage (:id doc))))))

    (testing "Delete document"
      (let [doc {:id "test:2" :name "Test Doc 2" :value 43}]
        (protocol/save-document storage doc)
        (protocol/delete-document storage (:id doc))
        (is (nil? (protocol/get-document storage (:id doc))))))

    (testing "Multiple documents"
      (let [docs [{:id "test:3" :name "Doc 3" :value 1}
                  {:id "test:4" :name "Doc 4" :value 2}]]
        (doseq [doc docs]
          (protocol/save-document storage doc))
        (is (= (first docs) (protocol/get-document storage "test:3")))
        (is (= (second docs) (protocol/get-document storage "test:4")))))

    (testing "Close storage"
      (.close storage)
      (is (nil? (protocol/get-document storage "test:1")))))) 