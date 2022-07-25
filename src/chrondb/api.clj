(ns chrondb.api)

;; TODO: remove me from here when we don't need databases-alpha atom
(defn rand-str [len]
  (apply str (take len (repeatedly #(char (+ (rand 26) 65))))))

;; THIS IS A EARLY STAGE FILE TO BUILD THE API
;; Don't mind it's structure, it's being used to draft the API
;; and we will rely on GIT for the real database.
;;
;; The structure here is used for development of the API design
;;
;; database desired structure:
;; {:repository-name {:branch-name ["commit-hash" {:data {key value}
;;                                                 :message ""
;;                                                 ...}]}}
(def databases-alpha (atom {}))

(defn commit
  [{:keys [repository branch message key value]}]
  (swap! databases-alpha
         update-in [(keyword repository) (keyword branch) (rand-str 10)]
         conj {:data {key value}
               :message message}))

(defn create-database
  "name -> it's the key for identifying the db and also the repository name
  TODO: create a URI parameter for the path when we  start dealing with fs"
  [name]
  (swap! databases-alpha assoc (keyword name) {:main {}}))
