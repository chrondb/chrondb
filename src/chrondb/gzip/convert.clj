(ns chrondb.gzip.convert
  "GZIP compression utilities for ChronDB.
   Provides functions for compressing and decompressing strings using GZIP."
  (:require [clojure.java.io :as io])
  (:import [java.io ByteArrayOutputStream ByteArrayInputStream]
           [java.util.zip GZIPInputStream GZIPOutputStream]))

(defn str->gzipped-bytes
  "Compresses a string using GZIP compression.
   Parameters:
   - str: The string to compress
   Returns: A byte array containing the GZIP compressed data"
  ^ByteArrayOutputStream [^String str]
  (with-open [out (ByteArrayOutputStream.)
              gzip (GZIPOutputStream. out)]
    (.write gzip (.getBytes str))
    (.finish gzip)
    (.toByteArray out)))

(defn gzipped-input-stream->str
  "Decompresses a GZIP compressed input stream to a string.
   Parameters:
   - input-stream: The input stream containing GZIP compressed data
   - encoding: Optional character encoding (default: UTF-8)
   Returns: The decompressed string"
  ^String
  [^ByteArrayInputStream input-stream & {:keys [^String encoding]
                                         :or   {encoding "UTF-8"}}]
  (with-open [out (ByteArrayOutputStream.)]
    (io/copy (GZIPInputStream. input-stream) out)
    (.close input-stream)
    (.toString out encoding)))
