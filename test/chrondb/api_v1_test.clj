(ns chrondb.api-v1-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.spec.alpha :as s]
            [chrondb.api-v1 :as api-v1]
            [clojure.pprint :as pp]))


(deftest hello
  (let [chronn (-> "chrondb:file://data/counter-v1"
                 (doto api-v1/delete-database
                       api-v1/create-database)
                 api-v1/connect)]
    (is (= ["db/created-at"]
          (-> (api-v1/select-keys (api-v1/db chronn)
                ["db/created-at"])
            keys
            (doto pp/pprint))))
    (is (= {"n" 42}
          (-> (api-v1/save chronn "n" 42)
            :db-after
            (api-v1/select-keys ["n"])
            (doto pp/pprint))))
    (is (= ["db/created-at"]
          (-> (api-v1/select-keys (api-v1/db chronn)
                ["db/created-at"])
            keys
            (doto pp/pprint))))))