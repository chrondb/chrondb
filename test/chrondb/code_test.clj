(ns chrondb.code-test
  (:require [clojure.test :refer [deftest is testing]]
            [clj-kondo.core :as kondo]))

(def *kondo-result
  (delay (kondo/run! {:lint ["src" "samples/counter/src"]})))

(deftest kondo
  (testing
   "clj-kondo"
    (is (= []
          (:findings @*kondo-result)))))
