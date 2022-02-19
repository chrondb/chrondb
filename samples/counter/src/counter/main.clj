(ns counter.main
  (:require [io.pedestal.http.route :as route]
            [io.pedestal.http :as http]
            [cheshire.core :as json]
            [chrondb.func :as func]
            [chrondb.api-v1 :as api-v1]
            [clojure.edn :as edn]))

(defn app-v0
  [{::keys [chronn]}]
  (let [routes (route/expand-routes
                #{["/" :get (fn [_]
                              {:body   (json/generate-string
                                        (merge {}
                                               (when-some [n (func/find-by-key chronn "n")]
                                                 {:n n})))
                               :status 200})
                   :route-name :current-state]
                  ["/" :post (fn [_]
                               (func/save chronn "n" (inc (or (func/find-by-key chronn "n")
                                                              0))
                                          :branch-name "lerolero2")
                               {:body   (json/generate-string {:n (func/find-by-key chronn "n")})
                                :status 200})
                   :route-name :inc]})]
    {::http/routes routes}))

(defn app-v1
  [{::keys [chronn]}]
  (let [routes (route/expand-routes
                #{["/" :get (fn [_]
                              (let [db (api-v1/db chronn)]
                                {:body   (-> db
                                           (api-v1/select-keys ["n"])
                                           json/generate-string)
                                 :status 200}))
                   :route-name :current-state]
                  ["/" :post (fn [_]
                               (let [db (api-v1/db chronn)
                                     {:keys [db-after]} (api-v1/save chronn "n" (inc (get (api-v1/select-keys db ["n"])
                                                                                       "n" 0)))]
                                 {:body   (-> db-after
                                            (api-v1/select-keys ["n"])
                                            json/generate-string)
                                  :status 200}))
                   :route-name :inc]})]
    {::http/routes routes}))

