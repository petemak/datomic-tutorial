;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; This file contains Clojure code for *exploratory* execution in the REPL
;;
;; It illustrates the following:
;;   1. Connecting to a datomic database
;;   2. Transacting schema and data
;;   3. Simple Datalog queries
;;   4. Datalog queries with implicit joins, parameters
;;   5. Retraction
;;   6. As-of/point-in-time queries
;;   7. Historical queries
;;
;; Dependencies for project.clj or deps.edn
;; :dependencies [[org.clojure/clojure "1.10.0"]
;;                [com.datomic/datomic-free "0.9.5697"]]
;; 
;;
;; Starting the Datomic Transactor and Console:
;; Step 1. Start transactor with property file
;;   $ ./bin/transactor ./config/transactor.properties
;;   ...
;;   Starting datomic:free://localhost:4334/<DB-NAME>, storing data in: data ...
;;   System started datomic:free://localhost:4334/<DB-NAME>, storing data in: data

;;
;; 2. Start Console -p for port, transactor name and uri
;;    $ ./bin/console -p <port> <name> <uri>
;;
;;    $ ./bin/console -p 8080 dev datomic:dev://localhost:4334/
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Load required name spaces
(require '[datomic.api :as d])
(require :reload '[datomic-tutorial.db-util :as u])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; 1. The database URL
(def db-uri "datomic:mem://hello")
;; (def db-uri "datomic:free://localhost:4334/tutorial")




;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; 2. Created database
;; NOTE: user datomic API d
(d/create-database db-uri)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; 3. Connect to the database
;; NOTE: connection can be held and resused!
(def conn (d/connect db-uri))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; 4. Load schema from EDN
;;
;; 
;; [{:db/ident :inv/sku
;;   :db/valueType :db.type/string
;;   :db/unique :db.unique/identity
;;   :db/cardinality :db.cardinality/one
;;   :db/doc "unique string identifier for a particular product"}
;;   ...]
(def schema (u/read-EDN "resources/db/schema.edn"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; 5. Generate seed data
;; Generates 64 maps of inventory units
;; SKU stands for "stock keeping units"
;;
;; {:inv/sku "SKU-1"
;;  :inv/type :shirt
;;  :inv/colour :green
;;  :inv/size :medium}
;;
(def data (u/gen-inv-data))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; 6. Trancat schema from EDN
;;
;; Note: returns {:db-before ... :db-after ... :tx-data [] :tempids {}}
(def schema-ret @(d/transact conn schema))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; 7. Trancat data 
;;
;; Note: returns {:db-before ... :db-after ... :tx-data [] :tempids {}}
(def data-ret @(d/transact conn data))

;; Noew we have a new database value we can query
(def new-db (d/db conn))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; 8. Basic query -  all SKUs
;; The datoms we want to query lookd as follows
;;
;; entity   Attribute     Value      Tx         op
;; [20      :inv/sku      SKU-7      100202     true]
;; [20      :inv/type     10         100202     true]
;; [20      :inv/colour   15         100202     true]
;; [20      :inv/size     17         100202     true]
;;
;; ---------------------------------------------------------------------
(def skus-q '[:find ?e ?sku
              :where [?e :inv/sku ?sku]])

;; How many entities?
(->> (d/q skus-q new-db)
     (count))

;; Show me just 3 of those
(->> (d/q skus-q new-db)
     (take 3))

;; Sorted 10
(->> (d/q skus-q new-db)
     (sort-by first)
     (take 10))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; 9. Parametrized query
;; find inventory iems with same colour as ?sku
;; Return their entity IDs, type, size and colour
;; The datoms we want to query lookd as follows
(def same-colour-p '[:find ?o ?tp ?sz ?co
                     :in $ ?sku
                     :where [?e :inv/sku ?sku]  ;;Match eid for SKU-7
                            [?e :inv/colour ?c] ;;Find colour of e
                            [?o :inv/colour ?c] ;;Find orthers with same colour
                            [?o :inv/type ?t]   ;;what's their type? ref
                            [?o :inv/size ?s]   ;;what's their size? ref   
                            [?t :db/ident ?tp]  ;;What's the actual type
                            [?s :db/ident ?sz]  ;;What's the actual size?
                            [?c :db/ident ?co]]);;What's the actual colour?

;; How many were found (we have 64 enitites nad 4 colurs)
(->> (d/q same-colour-p (d/db conn) "SKU-7")
     (count))

;; Show 3 of those
(->> (d/q same-colour-p (d/db conn) "SKU-7")
     (sort-by first)
     (take 3))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; 10. Accumulate: business calls and calls andy say they now want to
;; track orders as well:
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; 10.1 Order schema
;; An order will have
;; - one or many items
;;
;; An orderd item will have
;; - an id
;; - count the number of items
(def order-schema [{:db/ident :order/items
                    :db/valueType :db.type/ref
                    :db/cardinality :db.cardinality/many
                    :db/isComponent true
                    :db/doc "An order consists of one or more items"}
                   
                   {:db/ident :item/id
                    :db/valueType :db.type/ref
                    :db/cardinality :db.cardinality/one
                    :db/doc "An order item refers to a product"}
                   
                   {:db/ident :item/count
                    :db/valueType :db.type/long
                    :db/cardinality :db.cardinality/one
                    :db/doc "Number of items of a particular 
                              product in an order"}])

;; transact schema
(def ordsch-res @(d/transact conn order-schema))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; 10.2 Order data
;; For the order we will use a nested entity map {order [item item ...]}
;; The top level is an order. The nested level is a list of items.
;;
;; How do the persisisted EAVT index enries look like?
;; --------------------------------------------------------------
;; [E        A                V                T            Op]
;; --------------------------------------------------------------
;; [10       :item/id         EID-SKU-3        00100        true]
;; [10       :item/count      7                00100        true]
;; [11       :item/id         EID-SKU-17       00100        true]
;; [11       :item/count      3                00100        true]
;; [12       :order/items     [10 11]          00100        true]
;; ---------------------------------------------------------------------
(def order-data
  [{:order/items [{:item/id [:inv/sku "SKU-3"]
                   :item/count 7}
                  {:item/id [:inv/sku "SKU-17"]
                   :item/count 3}]}])


;; transact data
(def orddat-res @(d/transact conn order-data))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; 11. Query orders 
;;
;; Orders -> order items -> order id -> inventory id - SKU
(def all-orders-q
  '[:find ?ordid ?sku ?cnt
    :where [?ordid :order/items ?itmid]  ;; find all order's item ids
           [?itmid :item/id     ?invid]  ;; with the item ids find the inv ids 
           [?invid :inv/sku     ?sku]   ;; with the SKU ids find the SKU
           [?itmid :item/count  ?cnt]]) ;; with eids find the count,

;; How many orders are there ?
(->> (d/q all-orders-q (d/db conn))
     (count))


;; Only 2 show them all
(->> (d/q all-orders-q (d/db conn))
     (sort-by first))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; 12. Query items in same order
;;
;; If you know a unique attribute then you dont need to know the entity id.
;; A lookup ref will do the trick.   
;;
;; Exanple use case: suggest additional items to shoppers based on an
;;       inventory item they choose.
;; SKU -> inv id -> order item id -> others ids in same order -> SKUs
(def related-items-q '[:find ?oinv ?sku
                       :in $ ?inv
                       :where [?item   :item/id      ?inv]  ;; find the id of item
                              [?order  :order/items  ?item] ;; find order for item
                              [?order  :order/items  ?oitems] ;; find other items
                              [?oitems :item/id      ?oinv]   ;; find  inventory items 
                              [?oinv   :inv/sku      ?sku]]) ;; Find SKU o

;; How many orders are there ?
;; Note using a lookup ref [:inv/sku "SKU..."]
(->> (d/q related-items-q (d/db conn) [:inv/sku "SKU-17"])
     (count))

;; Only 2 show them all
(->> (d/q related-items-q (d/db conn) [:inv/sku "SKU-17"])
     (sort-by first))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; 13. Accumulate
;;
;; Business calls and says they now want to
;; track the inventory. Lets add counts for each item
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def inv-count-schema [{:db/ident :inv/count
                        :db/valueType :db.type/long
                        :db/cardinality :db.cardinality/one}])

;; transact count schema
(def invscm-res @(d/transact conn inv-count-schema))


;; With the account schma in place we can now assert that we have:
;;
;; SKU      Count
;; SKU-3    3
;; SKU-7    27
;; SKU-9    9
;;
(def inv-count-data
  [[:db/add [:inv/sku "SKU-3"] :inv/count 3]
   [:db/add [:inv/sku "SKU-7"] :inv/count 27]
   [:db/add [:inv/sku "SKU-9"] :inv/count 9]])

;; transact count data
(def invdat-res @(d/transact conn inv-count-data))


;; Query for SKUs with counts 
(def inv-count-q '[:find ?e ?sku ?cnt
                   :where [?e :inv/sku ?sku]
                          [?e :inv/count ?cnt]])

;; How many items in the inventory?
;; Note using a lookup ref [:inv/sku "SKU..."]
(->> (d/q inv-count-q (d/db conn))
     (count))

;; Only 3 show them all
(->> (d/q inv-count-q (d/db conn))
     (sort-by first))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; 14: Retract
;;
;; Lets assume we asserted the wrong count for SKU-9. It's sold out.
;; Retract the count for SKU-9 
;;
;; [:db/retract [lookup ref] arribute val]
(def corr-count-sku9-r
  [[:db/retract [:inv/sku "SKU-9"] :inv/count 9]
   [:db/add "datomic.tx" :db/doc "retract count for SKU-9"]])


;; Easy
(d/transact conn corr-count-sku9-r)


;; Show inventory counts again. SKU-9 should be gone 
(->> (d/q inv-count-q (d/db conn))
     (sort-by first))


;; Adn Count of SKU-3 mut be 1
;; We don't need to explicitly retract and then assert a new value
;; Since the cardinality is :cardinality/one we simply asser t the new
;; value. Datomic know that and will autmatically retract the old value
;; before asserting the new one
(def corr-count-sku3-r
  [[:db/add [:inv/sku "SKU-3"] :inv/count 1]
   [:db/add "datomic.tx" :db/doc "corrected count to 1"]])


;; Easy
(d/transact conn corr-count-sku3-r)


;; Show inventory counts again. SKU-3 should be 1 
(->> (d/q inv-count-q (d/db conn))
     (sort-by first))




;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; 15. History
;;
;; Datomic can provides 2 types of historical querries:
;; - as-of - any previous point in time. Time is either as an instant or
;;           a transaction id.
;; 
;; - history - entire history of your data
;;
;; ---------------------------------------------------------------------

;; ---------------------------------------------------------------------
;; 15.1 as-of querries
;; 
;; You don't need to remember the exact instant in time.
;; You can query the system the most recent transactions instead
;; and use those:
;; [?tx :db/txInstant]
;; ---------------------------------------------------------------------
(def last-3-tx-q '[:find (max 3 ?tx)
                   :where [?tx :db/txInstant]])

;; check 3 txids
(d/q last-3-tx-q (d/db conn))

;; Get earliets txid
(def txid (->> (d/q last-3-tx-q (d/db conn))
               (first)
               (first)
               (last)))


;; Get db as-of tx
(def db-as-of (d/as-of (d/db conn) txid))

;; Runds querry against (def db-as-of (d/as-of tx))
(d/q inv-count-q db-as-of)



;; ---------------------------------------------------------------------
;; 51.2 History query for inventory count asserstions and reractions
;; ---------------------------------------------------------------------
(def history-q '[:find ?e ?sku ?cnt ?tx ?op
                 :where [?e :inv/count ?cnt ?tx ?op]
                        [?e :inv/sku ?sku]])

;; Get db db-hist
(def db-hist (d/history (d/db conn)))

;; Show history of changes and related txs
(d/q history-q db-hist)

(->> (d/q history-q db-hist)
     (sort-by first))

