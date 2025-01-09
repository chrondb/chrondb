(ns chrondb.search.index
  (:require [clucie.core :as clucie]
            [clucie.store :as store]
            [clucie.analysis :as analysis]))

(def analyzer (analysis/standard-analyzer))

(defn store
  [& {:keys [type]
      :or {type "memory"}}]
  (store/memory-store))
