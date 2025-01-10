(ns chrondb.examples
  (:require [chrondb.storage.git :as git]
            [chrondb.index.lucene :as lucene]
            [chrondb.storage.protocol :as storage]
            [chrondb.index.protocol :as index]
            [talltale.core :as fake]
            [clojure.pprint :refer [pprint]]
            [clojure.java.io :as io])
  (:gen-class))

(defn ensure-directory [dir]
  (let [file (io/file dir)]
    (when-not (.exists file)
      (if (.mkdirs file)
        (println "Diretório criado:" (.getAbsolutePath file))
        (throw (ex-info (str "Não foi possível criar o diretório: " (.getAbsolutePath file))
                        {:directory dir}))))))

(defn create-chrondb []
  (println "\nCriando ChronDB...")
  (println "Criando diretório data...")
  (ensure-directory "data")
  (println "Criando diretório data/index...")
  (ensure-directory "data/index")
  (println "Criando storage...")
  (let [storage (git/create-git-storage "data")
        _ (println "Criando index...")
        index (lucene/create-lucene-index "data/index")]
    {:storage storage
     :index index}))

(defn generate-user []
  {:id (str "user:" (java.util.UUID/randomUUID))
   :name (fake/full-name :male :en)
   :email (fake/email)
   :age (+ 18 (rand-int 50))
   :address {:street (str (rand-int 999) " " (fake/street))
             :city (fake/city)
             :country "USA"
             :zip (fake/postal-code)}})

(defn save-user [chrondb user]
  (let [saved (storage/save-document (:storage chrondb) user)]
    (index/index-document (:index chrondb) saved)
    saved))

(defn search-users [chrondb query]
  (index/search (:index chrondb) query))

(defn -main [& args]
  (let [chrondb (create-chrondb)]
    (println "\nGerando e salvando 10 usuários aleatórios...")
    (doseq [_ (range 10)]
      (let [user (generate-user)
            saved (save-user chrondb user)]
        (println "\nUsuário salvo:")
        (pprint saved)))

    (println "\nBuscando usuários...")
    (let [results (search-users chrondb "John")]
      (println "\nResultados da busca por 'John':")
      (doseq [user results]
        (pprint user)))

    (.close (:storage chrondb))
    (.close (:index chrondb)))) 