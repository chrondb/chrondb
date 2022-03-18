(ns todo-app.main-test
  (:require [clojure.test :refer [deftest is]]
            [io.pedestal.http.route :as route]
            [io.pedestal.http :as http]
            [chrondb.api-v1 :as api-v1]
            [hiccup2.core :as h]
            [io.pedestal.test :refer [response-for]]
            [clojure.pprint :as pp]
            [ring.util.mime-type :as mime]
            [clojure.string :as string])
  (:import (org.jsoup Jsoup)
           (java.net URLDecoder)
           (java.nio.charset StandardCharsets)
           (java.util UUID)))

(defn expand-routes
  [{::keys [chronn]}]
  (let [get-todos (fn [_]
                    (let [db (api-v1/db chronn)
                          {:strs [todos-ids]} (api-v1/select-keys db ["todo-ids"])]
                      {:headers {"Content-Type" (mime/default-mime-types "html")}
                       :body    (->> [:html
                                      [:head]
                                      [:body
                                       [:ul {:id "todos"}
                                        (for [id todos-ids
                                              {:keys [note]} (api-v1/select-keys db [id])]
                                          [:li
                                           [:p.note
                                            note]])]]]
                                  (h/html {:mode :html})
                                  (str "<!DOCTYPE html>\n"))
                       :status  200}))

        add-todo (fn [{:keys [body]}]
                   (let [db (api-v1/db chronn)
                         {:keys [note]} (into {}
                                          (map (fn [kv]
                                                 (let [[k v] (map #(URLDecoder/decode (str %) StandardCharsets/UTF_8)
                                                               (string/split kv #"=" 2))]
                                                   [(keyword k) v])))
                                          (string/split (slurp body) #"&"))
                         {:strs [todos-ids]} (api-v1/select-keys db ["todos-ids"])
                         new-id (str "todos/" (UUID/randomUUID))]
                     (api-v1/save chronn "todos-ids" (conj (vec todos-ids)
                                                       (str new-id)))
                     (api-v1/save chronn new-id {"note" note})
                     {:headers {"Location" "/"}
                      :status  303}))
        route-spec #{["/" :get get-todos
                      :route-name :get-todos]
                     ["/todo" :post add-todo
                      :route-name :add-todo]}]
    (route/expand-routes route-spec)))

(deftest hello
  (let [chronn (-> "chrondb:file://data/todo-app"
                 (doto api-v1/delete-database
                       api-v1/create-database)
                 api-v1/connect)
        service-fn (-> {::http/routes (expand-routes {::chronn chronn})}
                     http/default-interceptors
                     http/dev-interceptors
                     http/create-servlet
                     ::http/service-fn)]
    (is (= 303
          (-> (response-for service-fn :post "/todo"
                :body "note=hello")
            :status)))
    (is (= 303
          (-> (response-for service-fn :post "/todo"
                :body "note=world")
            :status)))
    (is (= ["hello" "world"]
          (-> (response-for service-fn :get "/")
            :body
            str
            println
            #_#_#_Jsoup/parse
            (.select ".note")
            (->> (mapv (fn [x] (.text x))))
            (doto pp/pprint))))))