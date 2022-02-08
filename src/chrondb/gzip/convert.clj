(ns chrondb.gzip.convert
  (:require [clojure.java.io :as io])
  (:import [java.io ByteArrayOutputStream ByteArrayInputStream]
           [java.util.zip GZIPInputStream GZIPOutputStream]))

(defn str->gzipped-bytes
  "gzip string compression"
  ^ByteArrayOutputStream [^String str]
  (with-open [out (ByteArrayOutputStream.)
              gzip (GZIPOutputStream. out)]
    (.write gzip (.getBytes str))
    (.finish gzip)
    (.toByteArray out)))

(defn gzipped-input-stream->str
  "decompress gzip to string"
  ^String
  [^ByteArrayInputStream input-stream &{:keys [^String encoding]
                                        :or   {encoding "UTF-8"}}]
  (with-open [out (ByteArrayOutputStream.)]
    (io/copy (GZIPInputStream. input-stream) out)
    (.close input-stream)
    (.toString out encoding)))
