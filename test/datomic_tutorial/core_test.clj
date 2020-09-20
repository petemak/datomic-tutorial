(ns datomic-tutorial.core-test
  (:use [midje.sweet])
  (:require [datomic-tutorial.core :as c]))


;; Setup tests
(against-background
 [(before :contents (c/setup-db!))
  (after  :contents (c/delete-db!))]
 (fact "`setup-db!` creates a database and transacts schema and seed data"
       (some? @c/connection) => true
       (some? (:connection @c/connection)) => true
       (> (count (:resschema @c/connection)) 1) => true
       (> (count (:tx-data (:resdata @c/connection))) 200) => true
       (count (:tempids (:resdata @c/connection))) => 64))


;; Querry
(against-background
 [(before :contents (c/setup-db!))
  (after  :contents (c/delete-db!))]
 (fact "`same-colour` seed data querries must return items with same colour"
       (some? (c/same-colour "SKU-7" (:connection @c/connection))) => true
       (> (count (c/same-colour "SKU-7" (:connection @c/connection))) 1) => true
       (count  (c/same-colour "SKU-7"(:connection @c/connection) )) = 64))




(defn setup!
  "Set up database"
  []
  (let [res-setup (c/setup-db!)
        res-orderschema (c/assert-order-schema! (:connection @c/connection) c/order-schema)
        res-orderdata (c/assert-order-data! (:connection @c/connection) c/order-data)]
    @res-orderdata) )


(against-background
 [(before :contents (setup!))
  (after :contents (c/delete-db!))]
 (fact "related items to SKU-3 is SU-7. Else empty"
       (empty? (c/related-items (:connection @c/connection) "SKU-7")) => true
       (count (c/related-items (:connection @c/connection) "SKU-3")) => 2))



(comment

(require :reload '[datomic-tutorial.core :as c])

(def res-setup (c/setup-db!))

(def res-orderschema (c/assert-order-schema!
                      (:connection res-setup) c/order-schema))

(def res-orderdata (c/assert-order-data! (:connection res-setup) c/order-data))

;; Check connection
(c/all-orders (:connection res-setup))

;; items in the same order as...
(c/related-items (:connection res-setup) "SKU-7")

(c/related-items (:connection res-setup) "SKU-3")


  )
