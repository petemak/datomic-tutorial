(ns datomic-tutorial.db-util
  (:require [datomic.api :as d]))


;;-------------------------------------------------------------
;; Read schema from EDN file
;;  
;;-------------------------------------------------------------
(defn read-EDN
  "Reads schema or seed data from the specified source.
   file s. Note s must be EDN format and the location
  relatibe to the root of the application e.g
  resources/db/schema.edn"
  [s]
  (-> s
      (slurp)
      (read-string)))


;;-------------------------------------------------------------
;; Creating and connecting to the  database 
;;-------------------------------------------------------------
(defn create-db!
  "Create and connect to a database
   with the specied URI returning the
  connection"
  [dburi]
  (d/create-database dburi)
  (d/connect dburi))


