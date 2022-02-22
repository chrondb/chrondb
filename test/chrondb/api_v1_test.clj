(ns chrondb.api-v1-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.spec.alpha :as s]
            [chrondb.api-v1 :as api-v1]
            [clojure.pprint :as pp]
            [clojure.java.io :as io])
  (:import (java.io File)
           (java.time Clock)))


(deftest hello
  #_(doseq [^File f (reverse (file-seq (io/file "data")))]
      (.delete f))
  (binding [api-v1/*clock* (proxy [Clock] []
                             (instant []
                               (.toInstant #inst"2000")))]
    (let [chronn (-> "chrondb:file://data/counter-v1"
                   #_(doto api-v1/delete-database
                           api-v1/create-database)
                   api-v1/connect)]
      (pp/pprint (api-v1/repo->clj chronn))
      #_(is (= {"db/created-at" "2000-01-01T00:00:00Z"}
              (-> (api-v1/select-keys (api-v1/db chronn)
                    ["db/created-at"])
                (doto pp/pprint))))
      #_(is (= {"n" 42}
              (-> (api-v1/save chronn "n" 42)
                :db-after
                (api-v1/select-keys ["n"])
                (doto pp/pprint))))
      #_(is (= {"db/created-at" "2000-01-01T00:00:00Z"}
              (-> (api-v1/select-keys (api-v1/db chronn)
                    ["db/created-at"])
                (doto pp/pprint))))
      #_(is (= {"n" 42}
              (-> (api-v1/select-keys (api-v1/db chronn)
                    ["n"])
                (doto pp/pprint)))))))