(ns chrondb.search.index
  (:require [clucie.analysis :as analysis]
            [clucie.store :as store]))

(def analyzer (analysis/standard-analyzer))
(def store (store/memory-store)) ; or (store/disk-store "path/to/store")
