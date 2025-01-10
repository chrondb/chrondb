(ns chrondb.storage.memory
  "In-memory storage implementation for ChronDB.
   Uses ConcurrentHashMap for thread-safe document storage."
  (:require [chrondb.storage.protocol :as protocol])
  (:import [java.util.concurrent ConcurrentHashMap]))

(defrecord MemoryStorage [^ConcurrentHashMap data]
  "Memory-based implementation of the Storage protocol.
   Stores documents in a thread-safe ConcurrentHashMap."

  protocol/Storage
  (save-document [_ doc]
    "Saves a document to the in-memory store.
     Thread-safe operation using ConcurrentHashMap."
    (.put data (:id doc) doc)
    doc)

  (get-document [_ id]
    "Retrieves a document from the in-memory store by its ID.
     Returns nil if the document doesn't exist."
    (.get data id))

  (delete-document [_ id]
    "Removes a document from the in-memory store.
     Returns true if the document was found and deleted."
    (when (.containsKey data id)
      (.remove data id)
      true))

  java.io.Closeable
  (close [_]
    "Clears all documents from memory and releases resources."
    (.clear data)
    nil))

(defn create-memory-storage
  "Creates a new instance of MemoryStorage.
   Returns: A new MemoryStorage instance backed by a ConcurrentHashMap."
  []
  (->MemoryStorage (ConcurrentHashMap.))) 