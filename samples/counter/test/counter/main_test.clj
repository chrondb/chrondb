(ns counter.main-test
  (:require [chrondb.api-v1 :as api-v1]
            [chrondb.config :as config]
            [chrondb.func :as func]
            [clojure.pprint :as pp]
            [clojure.test :refer [deftest is]]
            [counter.main :as counter]
            [io.pedestal.http :as http]
            [io.pedestal.test :refer [response-for]]))

(set! *warn-on-reflection* true)

(deftest counter-v0
  #_(func/delete-database config/chrondb-local-git-dir)
  (let [chronn (func/path->repo config/chrondb-local-git-dir
                                :branch-name config/chrondb-local-repo-branch)
        service-fn (-> {::counter/chronn chronn}
                       counter/app-v0
                       http/default-interceptors
                       http/dev-interceptors
                       http/create-servlet
                       ::http/service-fn)]
    (is (= {:status 200, :body "{}"}
           (-> (response-for service-fn :get "/")
               (select-keys [:status :body])
               (doto pp/pprint))))
    (is (= {:status 200, :body "{\"n\":1}"}
           (-> (response-for service-fn :post "/")
               (select-keys [:status :body])
               (doto pp/pprint))))
    (is (= {:status 200, :body "{\"n\":1}"}
           (-> (response-for service-fn :get "/")
               (select-keys [:status :body])
               (doto pp/pprint))))))

(deftest counter-v1
  (let [chronn (-> "chrondb:file://data/counter-v1"
                 (doto api-v1/delete-database
                       api-v1/create-database)
                 api-v1/connect)
        service-fn (-> {::counter/chronn chronn}
                     counter/app-v1
                     http/default-interceptors
                     http/dev-interceptors
                     http/create-servlet
                     ::http/service-fn)]
    (is (= {:status 200, :body "{}"}
          (-> (response-for service-fn :get "/")
            (select-keys [:status :body])
            #_(doto pp/pprint))))
    (is (= {:status 200, :body "{\"n\":1}"}
          (-> (response-for service-fn :post "/")
            (select-keys [:status :body])
            #_(doto pp/pprint))))
    (is (= {:status 200, :body "{\"n\":1}"}
          (-> (response-for service-fn :get "/")
            (select-keys [:status :body])
            #_(doto pp/pprint))))
    (is (= {:status 200, :body "{\"n\":2}"}
          (-> (response-for service-fn :post "/")
            (select-keys [:status :body])
            #_(doto pp/pprint))))
    (is (= {:status 200, :body "{\"n\":2}"}
          (-> (response-for service-fn :get "/")
            (select-keys [:status :body])
            #_(doto pp/pprint))))))
