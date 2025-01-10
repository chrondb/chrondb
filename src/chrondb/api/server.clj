(ns chrondb.api.server
  (:require [chrondb.api.v1.routes :as routes]
            [ring.adapter.jetty :as jetty]
            [clojure.data.json :as json]))

(defn wrap-json-body-custom [handler]
  (fn [request]
    (if-let [body (:body request)]
      (try
        (let [body-str (if (string? body) body (slurp body))
              json-body (when (not-empty body-str)
                          (json/read-str body-str :key-fn keyword))]
          (handler (assoc request :body json-body)))
        (catch Exception e
          {:status 400
           :body {:error (str "Invalid JSON: " (.getMessage e))}}))
      (handler request))))

(defn wrap-json-response [_]
  (fn [response]
    (if (coll? (:body response))
      (-> response
          (update :body json/write-str)
          (assoc-in [:headers "Content-Type"] "application/json"))
      response)))

(defn create-app [storage index]
  (-> (routes/create-routes storage index)
      wrap-json-body-custom
      wrap-json-response))

(defn start-server [storage index port]
  (println "Starting server on port" port)
  (jetty/run-jetty (create-app storage index) {:port port :join? false})) 