(ns chrondb.api.v1
  (:require [chrondb.storage.protocol :as storage]
            [chrondb.index.protocol :as index]
            [clojure.data.json :as json])
  (:import (org.eclipse.jgit.lib Repository Constants FileMode ObjectId ObjectInserter RefUpdate TreeFormatter CommitBuilder PersonIdent)
           (org.eclipse.jgit.treewalk TreeWalk)))

(defn wrap-json-response [handler]
  (fn [request]
    (let [response (handler request)]
      (-> response
          (assoc :headers {"Content-Type" "application/json"})
          (update :body json/write-str)))))

(defn create-routes [storage index]
  {"GET /api/v1/get/:key" {:handler (fn [{{:keys [key]} :path-params}]
                                     {:status 200
                                      :body (storage/get-value storage key)})}
   
   "POST /api/v1/save" {:handler (fn [{:keys [body-params]}]
                                  (let [{:keys [key value fields]} body-params
                                        saved (storage/save storage key value)]
                                    (when fields
                                      (index/index! index saved fields))
                                    {:status 200
                                     :body saved}))}
   
   "DELETE /api/v1/delete/:key" {:handler (fn [{{:keys [key]} :path-params}]
                                          (storage/delete storage key)
                                          {:status 204})}
   
   "GET /api/v1/search" {:handler (fn [{:keys [query-params]}]
                                   (let [{:keys [q limit]} query-params
                                         limit (or (some-> limit Integer/parseInt) 10)]
                                     {:status 200
                                      :body (index/search index q limit)}))}}) 