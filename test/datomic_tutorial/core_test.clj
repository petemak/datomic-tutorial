(ns datomic-tutorial.core-test
  (:use [midje.sweet])
  (:require [datomic-tutorial.core :as c]))


;; Setup tests
(against-background
 [(before :contents (c/setup-db!))
  (after  :contents (c/teardown-db!))]
 (fact "`setup-db!` creates a database and transacts schema and seed data"
       (some? @c/connection) => true
       (some? (:connection @c/connection)) => true
       (> (count (:resschema @c/connection)) 1) => true
       (> (count (:tx-data (:resdata @c/connection))) 200) => true
       (count (:tempids (:resdata @c/connection))) => 64))


;; Querry
(against-background
 [(before :contents (c/setup-db!))
  (after  :contents (c/teardown-db!))]
 (fact "`same-colour` seed data querries must return items with same colour"
       (some? (c/same-colour "SKU-7" (:connection @c/connection))) => true
       (> (count (c/same-colour "SKU-7" (:connection @c/connection))) 1) => true
       (count  (c/same-colour "SKU-7"(:connection @c/connection) )) = 64))



(against-background
 [(before :contents (c/setup-orders-if-not-exist! (:connection @c/connection)))
  (after :contents (c/teardown-db!))]
 (fact "related items to SKU-3 is SU-7. Else empty"
       (empty? (c/related-items (:connection @c/connection) "SKU-7")) => true
       (count (c/related-items (:connection @c/connection) "SKU-3")) => 2))


(against-background
 [(before :contents (c/setup-orders-if-not-exist! (:connection @c/connection)))
  (after :contents (c/teardown-db!))]
 (fact "Related items to SKU-3 is SU-7 using rules. Else empty"
       (empty? (c/related-items2 (:connection @c/connection) "SKU-7")) => true
       (count (c/related-items2 (:connection @c/connection) "SKU-3")) => 2))



