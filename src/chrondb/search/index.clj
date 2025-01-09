(ns chrondb.search.index
  (:require [clucie.store :as store]
            [clucie.analysis :as analysis]))

(def analyzer (analysis/standard-analyzer))

(defn store
  [& _]
  (store/memory-store))
