(ns chrondb.search.index
  (:require [clucie.analysis :as analysis]
            [clucie.store :as store]))

(def analyzer
  "lucene analyzer"
  (analysis/standard-analyzer))

(defn store
  "lucene store (memory or disk), if the type is other than `memory` it must be the path that will be saved"
  [&{:keys [type]
     :or {type "memory"}}]
  (if (identical? type "memory")
    (store/memory-store)
    (store/disk-store type)))
