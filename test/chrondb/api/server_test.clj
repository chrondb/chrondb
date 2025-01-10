(ns chrondb.api.server-test
  (:require [chrondb.api.server :as server]
            [chrondb.test-helpers :refer [with-test-data]]
            [clojure.data.json :as json]
            [clojure.test :refer :all]
            [ring.mock.request :as mock])
  (:import (clojure.lang DynamicClassLoader)))

(declare ^:dynamic storage)
(declare ^:dynamic index)

(defn json-request [request]
  (-> request
      (assoc-in [:headers "content-type"] "application/json")
      (assoc-in [:headers "accept"] "application/json")))

(defn parse-json-body [response]
  (when-let [body (:body response)]
    (if (string? body)
      (json/read-str body :key-fn keyword)
      body)))

(defn create-json-request [method uri & [body]]
  (cond-> (mock/request method uri)
    true json-request
    body (assoc :body (json/write-str body))))

(deftest test-server-endpoints
  (with-test-data [storage index]
    (let [app (server/create-app storage index)
          doc {:id "test:1" :name "Test Doc" :value 42}]

      (testing "Save endpoint"
        (let [response (app (create-json-request :post "/api/v1/save" doc))]
          (is (= 200 (:status response)))
          (is (= doc (parse-json-body response)))))

      (testing "Get endpoint"
        (let [response (app (create-json-request :get "/api/v1/get/test:1"))]
          (is (= 200 (:status response)))
          (is (= doc (parse-json-body response)))))

      (testing "Search endpoint"
        (let [response (app (create-json-request :get "/api/v1/search?q=Test"))
              body (parse-json-body response)
              results (:results body)]
          (is (= 200 (:status response)))
          (is (= 1 (count results)))
          (is (= doc (first results)))))

      (testing "Delete endpoint"
        (let [response (app (create-json-request :delete "/api/v1/delete/test:1"))]
          (is (= 200 (:status response)))
          (is (= {:message "Document deleted"} (parse-json-body response))))))))

(deftest test-server-error-handling
  (with-test-data [storage index]
    (let [app (server/create-app storage index)]

      (testing "Get non-existent document"
        (let [response (app (create-json-request :get "/api/v1/get/nonexistent"))]
          (is (= 404 (:status response)))
          (is (= {:error "Document not found"} (parse-json-body response)))))

      (testing "Delete non-existent document"
        (let [response (app (create-json-request :delete "/api/v1/delete/nonexistent"))]
          (is (= 404 (:status response)))
          (is (= {:error "Document not found"} (parse-json-body response)))))

      (testing "Invalid JSON in save request"
        (let [response (app (-> (mock/request :post "/api/v1/save")
                                json-request
                                (assoc :body "{invalid json")))]
          (is (= 400 (:status response)))
          (is (= {:error "Invalid JSON"} (parse-json-body response)))))))) 