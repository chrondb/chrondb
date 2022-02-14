(ns chrondb.gzip.convert-test
  (:require [clojure.test :refer [deftest is testing]]
            [chrondb.gzip.convert :as convert]))

(def test-str "π3.14")
(def gzipped (convert/str->gzipped-bytes test-str))

(deftest str-to-gzip
  (testing "convert string -> gzip"
    (is (= (type (byte-array []))
           (type (convert/str->gzipped-bytes test-str))))
    (is (bytes? (convert/str->gzipped-bytes test-str)))))

(deftest gzip-to-str
  (testing "convert gzip -> string"
    (is (= test-str
           (convert/gzipped-input-stream->str
            (java.io.ByteArrayInputStream. gzipped)))))
  (testing "check encode UTF-16"
    (is (= "쾀㌮ㄴ"
           (convert/gzipped-input-stream->str
            (java.io.ByteArrayInputStream. gzipped)
            :encoding "UTF-16")))))
