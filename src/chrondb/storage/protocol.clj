(ns chrondb.storage.protocol)

(defprotocol Storage
  "Protocol defining the storage operations for ChronDB"
  (save-document [this doc]
    "Save a document to storage. Returns the saved document.")
  
  (get-document [this id]
    "Retrieve a document by its ID. Returns nil if not found.")
  
  (delete-document [this id]
    "Delete a document by its ID. Returns true if successful, false otherwise.")
  
  (close [this]
    "Close any resources associated with the storage.")) 