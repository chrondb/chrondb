(ns chrondb.api.v1.routes
  (:require [compojure.core :refer [GET POST DELETE routes]]
            [compojure.route :as route]
            [chrondb.storage.protocol :as storage]
            [chrondb.index.protocol :as index]
            [ring.util.response :as response]))

(defn handle-save [storage index doc]
  (try
    (if (and (map? doc) (:id doc))
      (let [saved (storage/save-document storage doc)]
        (index/index-document index saved)
        (response/response saved))
      (response/status
       (response/response {:error "Invalid document format. Must be a map with an :id field"})
       400))
    (catch Exception e
      (response/status
       (response/response {:error (.getMessage e)})
       500))))

(defn handle-get [storage id]
  (if-let [doc (storage/get-document storage id)]
    (response/response doc)
    (response/status
     (response/response {:error "Document not found"})
     404)))

(defn handle-delete [storage index id]
  (if-let [_ (storage/get-document storage id)]
    (do
      (storage/delete-document storage id)
      (index/delete-document index id)
      (response/response {:message "Document deleted"}))
    (response/status
     (response/response {:error "Document not found"})
     404)))

(defn handle-search [index query]
  (response/response
   {:results (index/search index query)}))

(defn create-routes [storage index]
  (routes
   (GET "/" [] (response/response {:message "Welcome to ChronDB"}))
   (POST "/api/v1/save" {body :body} (handle-save storage index body))
   (GET "/api/v1/get/:id" [id] (handle-get storage id))
   (DELETE "/api/v1/delete/:id" [id] (handle-delete storage index id))
   (GET "/api/v1/search" [q] (handle-search index q))
   (route/not-found (response/not-found {:error "Not Found"})))) 