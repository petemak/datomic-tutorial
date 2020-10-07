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


;;-------------------------------------------------------------
;; 1. Schema uses :db/ident to define a programatic identifiers
;; First we define identifiers for inventory identifieers type, colour and size.
;;
;; Inventory types:
;;  {:db/ident :shirt}
;;  {:db/ident :trousers}
;;  {:db/ident :dress}
;;  {:db/ident :hat}
;;
;; Next Inventory attributes like SKU, type, colour and size:
;;
;;  {:db/ident :inv/sku
;;   :db/valueType :db.type/string
;;   :db/unique :db.unique/identity
;;   :db/cardinality :db.cardinality/one
;;   :db/doc "unique string identifier for a particular product"}
;; 
;;  {:db/ident :inv/type
;;   :db/valueType :db.type/ref
;;   :db/cardinality :db.cardinality/oney
;;   :db/doc "Type of inventory item"}
;; 
;; See resources/db/schema.edn
;;  
;;
;; Generate a combination of maps from 4, types,
;; 4 colours and 4 sizes.
;; => 4 x 4 x 4 = 64 maps
;;-------------------------------------------------------------
(defn gen-inv-data
  "Generate sample data
  64 maps:
  {:inv/sku \"SKU-1\"
   :inv/type :shirt
   :inv/colour :green
   :inv/size :medium}"
  []
  (let [maps (for [t [:shirt :trousers :dress :hat]
                   c [:red   :green    :blue  :yellow]
                   s [:small :medium   :large :xlarge]]
               {:inv/type t
                :inv/colour c
                :inv/size s })]
    (->> maps         
         (map-indexed (fn [idx map]
                        (assoc map :inv/sku (str "SKU-" idx))))
         vec)))


