(ns chrondb.gzip.convert-test
  (:require [clojure.test :refer [deftest is testing]]
            [chrondb.gzip.convert :refer [str->gzipped-bytes gzipped-input-stream->str]]))

(def test-str "π3.14")
(def gzipped (str->gzipped-bytes test-str))

(deftest str-to-gzip
  (testing "convert string -> gzip"
    (is (= (type (byte-array []))
           (type (str->gzipped-bytes test-str))))
    (is (bytes? (str->gzipped-bytes test-str)))))

(deftest gzip-to-str
  (testing "convert gzip -> string"
    (is (= test-str
           (gzipped-input-stream->str
             (java.io.ByteArrayInputStream. gzipped)))))
  (testing "check encode UTF-16"
    (is (= "쾀㌮ㄴ"
           (gzipped-input-stream->str
             (java.io.ByteArrayInputStream. gzipped)
             :encoding "UTF-16")))))