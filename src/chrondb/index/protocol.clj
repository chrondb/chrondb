(ns chrondb.index.protocol)

(defprotocol Index
  (index-document [this doc] "Index a document")
  (delete-document [this id] "Delete a document from the index")
  (search [this query] "Search for documents matching the query")) 