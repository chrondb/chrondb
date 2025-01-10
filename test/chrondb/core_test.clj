(ns chrondb.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [chrondb.storage.memory :as memory]
            [chrondb.index.lucene :as lucene]))

(deftest test-chrondb
  (testing "ChronDB operations"
    (let [storage (memory/create-memory-storage)
          index (lucene/create-lucene-index "test-index")
          chrondb {:storage storage :index index}]
      (is (= chrondb {:storage storage :index index}))
      (.close storage)
      (.close index)))) 