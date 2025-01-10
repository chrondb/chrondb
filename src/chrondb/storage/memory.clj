(ns chrondb.storage.memory
  (:require [chrondb.storage.protocol :as protocol])
  (:import (java.util.concurrent ConcurrentHashMap)))

(defrecord MemoryStorage [^ConcurrentHashMap data]
  protocol/Storage
  (save-document [_ doc]
    (let [id (:id doc)]
      (.put data id doc)
      doc))

  (get-document [_ id]
    (.get data id))

  (delete-document [_ id]
    (.remove data id))

  java.io.Closeable
  (close [this]
    (.clear data)
    nil))

(defn create-memory-storage []
  (->MemoryStorage (ConcurrentHashMap.))) 