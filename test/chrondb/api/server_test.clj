(ns chrondb.api.server-test
  (:require [clojure.test :refer [deftest is testing]]
            [chrondb.api.server :as server]
            [chrondb.storage.memory :as memory]
            [chrondb.index.lucene :as lucene]))

(deftest test-server
  (testing "Server creation"
    (let [storage (memory/create-memory-storage)
          index (lucene/create-lucene-index "test-index")
          app (server/create-app storage index)]
      (is (fn? app))
      (.close storage)
      (.close index)))) 