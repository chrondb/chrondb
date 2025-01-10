(ns chrondb.storage.memory
  (:require [chrondb.storage.protocol :as protocol])
  (:import [java.util.concurrent ConcurrentHashMap]))

(defrecord MemoryStorage [^ConcurrentHashMap data]
  protocol/Storage
  (save-document [_ doc]
    (.put data (:id doc) doc)
    doc)

  (get-document [_ id]
    (.get data id))

  (delete-document [_ id]
    (when (.containsKey data id)
      (.remove data id)
      true))

  java.io.Closeable
  (close [_]
    (.clear data)
    nil))

(defn create-memory-storage []
  (->MemoryStorage (ConcurrentHashMap.))) 