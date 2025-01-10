(ns chrondb.api.server
  (:require [chrondb.api.v1.routes :as routes]
            [ring.middleware.json :refer [wrap-json-response wrap-json-body]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.adapter.jetty :as jetty]
            [clojure.java.io :as io]
            [clojure.data.json :as json]))

(defn wrap-json-body-custom [handler]
  (fn [request]
    (if-let [body (:body request)]
      (if (string? body)
        (try
          (let [json-body (json/read-str body :key-fn keyword)]
            (handler (assoc request :body json-body)))
          (catch Exception _
            {:status 400
             :headers {"Content-Type" "application/json"}
             :body (json/write-str {:error "Invalid JSON"})}))
        (handler request))
      (handler request))))

(defn wrap-json-response-custom [handler]
  (fn [request]
    (let [response (handler request)]
      (if (string? (:body response))
        response
        (-> response
            (update :body json/write-str)
            (assoc-in [:headers "Content-Type"] "application/json"))))))

(defn create-app [storage index]
  (-> (routes/create-routes storage index)
      wrap-keyword-params
      wrap-params
      wrap-json-body-custom
      wrap-json-response-custom))

(defn start-server [storage index port]
  (println "Starting ChronDB server on port" port)
  (jetty/run-jetty (create-app storage index)
                   {:port port :join? false})) 