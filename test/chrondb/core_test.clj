(ns chrondb.core-test
  (:require [chrondb.core :as core]
            [chrondb.test-helpers :refer [with-test-data]]
            [clojure.test :refer :all]))

(deftest test-main-function
  (with-test-data [storage index]
    (testing "Main function starts server"
      (let [server (core/-main "3000")]
        (is (instance? org.eclipse.jetty.server.Server server))
        (.stop server))))) 