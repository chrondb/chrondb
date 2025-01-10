(ns chrondb.api.v1.routes-test
  (:require [chrondb.api.v1.routes :as routes]
            [chrondb.api.server :as server]
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

(deftest test-routes-creation
  (with-test-data [storage index]
    (let [app (server/create-app storage index)]
      (testing "Root endpoint returns 200"
        (let [response (app (create-json-request :get "/"))]
          (is (= 200 (:status response)))
          (is (= {:message "Welcome to ChronDB"} (parse-json-body response))))))))

(deftest test-save-endpoint
  (with-test-data [storage index]
    (let [app (server/create-app storage index)
          user {:id "user:1"
                :name "John Doe"
                :age 30
                :email "john@example.com"}]
      (testing "Save valid document"
        (let [response (app (create-json-request :post "/api/v1/save" user))]
          (is (= 200 (:status response)))
          (is (= user (parse-json-body response))))))))

(deftest test-get-endpoint
  (with-test-data [storage index]
    (let [app (server/create-app storage index)
          user {:id "user:1"
                :name "John Doe"
                :age 30
                :email "john@example.com"}
          _ (app (create-json-request :post "/api/v1/save" user))]
      (testing "Get existing document"
        (let [response (app (create-json-request :get "/api/v1/get/user:1"))]
          (is (= 200 (:status response)))
          (is (= user (parse-json-body response))))))))

(deftest test-delete-endpoint
  (with-test-data [storage index]
    (let [app (server/create-app storage index)
          user {:id "user:1"
                :name "John Doe"
                :age 30
                :email "john@example.com"}
          _ (app (create-json-request :post "/api/v1/save" user))]
      (testing "Delete existing document"
        (let [response (app (create-json-request :delete "/api/v1/delete/user:1"))]
          (is (= 200 (:status response)))
          (is (= {:message "Document deleted"} (parse-json-body response))))))))

(deftest test-search-endpoint
  (with-test-data [storage index]
    (let [app (server/create-app storage index)
          user {:id "user:1"
                :name "John Doe"
                :age 30
                :email "john@example.com"}
          _ (app (create-json-request :post "/api/v1/save" user))]
      (testing "Search indexed documents"
        (let [response (app (create-json-request :get "/api/v1/search?q=John"))
              results (:results (parse-json-body response))]
          (is (= 200 (:status response)))
          (is (= 1 (count results)))
          (is (= user (first results)))))))) 