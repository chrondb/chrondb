(ns chrondb.test-data
  (:require [talltale.core :as faker]))

(def test-search-username 
  ((faker/person :en) :username))

(def sample-data
  [{:username ((faker/person :en) :username)
    :age      ((faker/person :en) :age)}
   {:username ((faker/person :en) :username)
    :age      ((faker/person :en) :age)}
   {:username ((faker/person :en) :username)
    :age      ((faker/person :en) :age)}
   {:username ((faker/person :en) :username)
    :age      ((faker/person :en) :age)}
   {:username ((faker/person :en) :username)
    :age      ((faker/person :en) :age)}])

(def sample-data-with-test-user
  (conj sample-data
        {:username test-search-username
         :age      ((faker/person :en) :age)})) 