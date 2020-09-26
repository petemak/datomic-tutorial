(ns datomic-tutorial.core
  (:require [datomic.api :as d]
            [datomic-tutorial.db-util :as u]))

;;--------------------------------------------------------------
;;
;;--------------------------------------------------------------
(def connection (atom nil))

;;--------------------------------------------------------------
;; 1. Get datomic-pro starter
;; 2. Extract 
;; 3. Copy a transactor.properties and specify license-key
;; 4. Start transactor by specifying the property file
;;   $ ./bin/transactor ./config/transactor.properties
;;   Launching with Java options -server -Xms1g -Xmx1g -XX:...
;;   Starting datomic:dev://localhost:4334/<DB-NAME>, storing
;; data in: data ...
;;   System started datomic:dev://localhost:4334/<DB-NAME>,
;;   storing data in: data
;;
;; 5. Start Console -p for port, transactor name and uri
;;    $ ./bin/console -p <port> <name> <uri>
;;    
;; $ ./bin/console -p 8080 dev datomic:dev://localhost:4334/
;; Console started on port: 8080
;;    dev = datomic:dev://localhost:4334/
;; Open http://localhost:8080/browse in your browser
;; (Chrome recommended)
;;--------------------------------------------------------------
(def db-uri "datomic:mem://hello")
;;(def db-uri "datomic:free://localhost:4334/tutorial")


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
;;  :db/doc "unique string identifier for a particular product"}
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


;;-------------------------------------------------------------
;; 2. Create, connect to the database then 
;; Seed database with test data
;;-------------------------------------------------------------
(defn setup-db!
  "Initialise database, transcts schema and
   seeds it with inventory data and stores
   the connection and db state in the atom"
  []
  (let [schema (u/read-EDN "resources/db/schema.edn")
        data (gen-inv-data)
        conn (u/create-db! db-uri)
        ress @(d/transact conn schema)
        resd @(d/transact conn data)]
    (reset! connection {:connection conn :resschema ress :resdata resd})))



;;-------------------------------------------------------------
;; Required only during REPL sessions
;;-------------------------------------------------------------
(defn teardown-db!
  "Clean up database"
  []
  (try
    (d/delete-database db-uri)
    (reset! connection nil)
    (catch Exception e
        (str "::-> delete-db! failed: " (.getMessage e)))))

;; ---------------------------------------------------------------------
;; 3. Querrying the database 
;; 
;; Datomic maintains the entire history of your data.
;; From this, you can query against a database value
;; as of a particular point in time.
;; 
;; The db API returns the latest "database value" from
;; a connection.
;;
;; (def db (d/db conn))
;;
;; The datoms we want to query lookd as follows
;;
;; entity   Attribute     Value      Tx         op
;; [20      :inv/sku      SKU-7      100202     true]
;; [20      :inv/type     10         100202     true]
;; [20      :inv/colour   15         100202     true]
;; [20      :inv/size     17         100202     true]
;;
;; The values of the attributes type, colour and size are refs
;; and therefore entity IDs for :db/idents
;; ---------------------------------------------------------------------
;;
;; ---------------------------------------------------------------------
;; Query all SKUs
;; ---------------------------------------------------------------------
(def all-skus-q '[:find ?e ?sku
                  :where [?e :inv/sku ?sku]])

;; ---------------------------------------------------------------------
;; Find SKUs with same colour as SKU-7
;; Return their entity IDs, type, size and colour
;;What's the actual colour?
;; ---------------------------------------------------------------------
(def same-colourSKU7 '[:find ?o ?tp ?sz ?co
                       :where [?e :inv/sku "SKU-7"] ;;Find eid SKU-7
                              [?e :inv/colour ?c]  ;;Find colour of e
                              [?o :inv/colour ?c]  ;;Find orthers same colour
                              [?o :inv/type ?t]    ;;what's their type? ref
                              [?o :inv/size ?s]    ;;what's their size? ref   
                              [?t :db/ident ?tp]   ;;What's the actual type
                              [?s :db/ident ?sz]   ;;What's the actual size?
                              [?c :db/ident ?co]]) ;;What's the actual colour?


;; ---------------------------------------------------------------------
;; Parametrised query: find SKUs with same colour.
;; Return their entity IDs, type, size and colour
;; ---------------------------------------------------------------------
(def same-colour-pq '[:find ?o ?tp ?sz ?co
                      :in $ ?sku
                      :where [?e :inv/sku ?sku]  ;;Match eid for SKU-7
                             [?e :inv/colour ?c] ;;Find colour of e
                             [?o :inv/colour ?c] ;;Find orthers with same colour
                             [?o :inv/type ?t]   ;;what's their type? ref
                             [?o :inv/size ?s]   ;;what's their size? ref   
                             [?t :db/ident ?tp]  ;;What's the actual type
                             [?s :db/ident ?sz]  ;;What's the actual size?
                             [?c :db/ident ?co]]);;What's the actual colour?


;; ---------------------------------------------------------------------
;; Call same colour
;; ---------------------------------------------------------------------
(defn same-colour
  "Query all inventory with same colour"
  [sku conn]
  (if (some? sku)
    (d/q same-colour-pq (d/db conn) sku)
    (d/q same-colourSKU7 (d/db conn))))


;; ---------------------------------------------------------------------
;; 4. Accumulate: track orders as well
;;
;; An order will have
;; - one or many items
;;
;; An item will have
;; - an id
;; - count the number of items
;; ---------------------------------------------------------------------
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

(defn assert-order-schema!
  "Accumulation of schema. Add oder and items schema"
  [conn schema]
  (d/transact conn schema))

;; ---------------------------------------------------------------------
;; 4.1 The order data.
;; We use a nested entity map {order [item item ...]}
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
  {:order/items [{:item/id [:inv/sku "SKU-3"]
                  :item/count 7}
                 {:item/id [:inv/sku "SKU-17"]
                  :item/count 3}]})

(defn assert-order-data!
  "Accumulation of data. Add order data"
  [conn data]
  (d/transact conn [order-data]))



;; ---------------------------------------------------------------------
;; 4.2 Accumulation: add order schema and seed data for orders
;; ---------------------------------------------------------------------
(defn setup-orders!
  "Assert order schema and seed data"
  [conn]
  (let [res-orderschema (assert-order-schema! conn  order-schema)
        res-orderdata (assert-order-data! conn order-data)]
    @res-orderdata))



;; ---------------------------------------------------------------------
;; Accumulation: add order schema and seed data for orders
;; This version ensures the DB is initiated if it does not exists
;; ---------------------------------------------------------------------
(defn setup-orders-if-not-exist!
  "Ensures databse is initiated if not done before"
  [conn]
  (if (some? conn)
    (setup-orders! conn)
    (let [res (setup-db!)]
      (setup-orders! (:connection res)))))


;; ---------------------------------------------------------------------
;; 4.3 Task: retriev all order and number of items
;;
;; --------------------------------------------------------------------
(def all-orders-q '[:find ?e ?sku ?cnt
                    :where [?e :order/items  ?eit]  ;; find all order's item eids
                           [?eit :item/id    ?esk]  ;; with the eids find the SKU ids 
                           [?esk :inv/sku    ?sku]   ;; with the SKU ids find the SKU
                           [?eit :item/count ?cnt]]) ;; with eids find the count,

(defn all-orders
  "Query all orders"
  [conn]
  (d/q all-orders-q (d/db conn)))


;; ---------------------------------------------------------------------
;; 5. Parametrised quesries
;; 
;; Task: suggest additional items to shoppers based on an
;;       inventory item they choose.
;;
;; So we need a query that, given any inventory item, finds all
;; the other items that have ever appeared in the same order.
;;
;; Lets query all orders and related items
;; --------------------------------------------------------------------
(def related-items-q '[:find ?oinv ?sku
                       :in $ ?inv
                       :where [?item   :item/id      ?inv]  ;; find the id of item
                              [?order  :order/items  ?item] ;; find order for item
                              [?order  :order/items  ?oitems] ;; find other items
                              [?oitems :item/id      ?oinv]   ;; find  inventory items 
                              [?oinv   :inv/sku      ?sku]]) ;; Find SKU o

;; ---------------------------------------------------------------------
;; Datomic performs automatic resolution of entity identifiers,
;; so entity ids, idents, and lookup refs cna be used interchangeably.
;;
;; If you know a unique attribute then you dont need to know the entity id.
;; A lookup ref will do the trick.
;;
;; A lookup ref is a two element list of unique attribute + value uniquely
;; identifies an entity [:inv/sku  "SKU-7"]
;; ---------------------------------------------------------------------
(defn related-items
  "Query all orders. Uses a query ref "
  [conn sku]
  (d/q related-items-q (d/db conn) [:inv/sku sku]))

;; ---------------------------------------------------------------------
;;6.  Rules
;;
;; Datomic datalog allows to package up sets of :where clauses into
;; named rules. These rules make query logic reusable, and also composable,
;; meaning that you can bind portions of a query's logic at query time. 
;; ---------------------------------------------------------------------
(def ordered-together-rule
  '[[(ordered-together ?inv ?other-inv)
     [?item  :item/id ?inv]
     [?order :order/items ?item]
     [?order :order/items ?other-item]
     [?other-item :item/id ?other-inv]]])

;; ---------------------------------------------------------------------
;; pass these rules to a query, using the special
;; :in name %, and then refer to the rules by name
;; ---------------------------------------------------------------------
(def related-items-q2 '[:find ?sku
                        :in $ % ?inv
                        :where (ordered-together ?inv ?other-inv)
                               [?other-inv :inv/sku ?sku]])

;; ---------------------------------------------------------------------
;; pass these rules to a query, using the special
;; :in name %, and then refer to the rules by name
;; ---------------------------------------------------------------------
(defn related-items2
  "Related items to spicified SKU"
  [conn sku]
  (d/q related-items-q2
       (d/db conn)
       ordered-together-rule
       [:inv/sku sku]))


;; ---------------------------------------------------------------------
;; 7. Retracts
;;
;; Lets start by accumlating to add schema for inventory counts
;; Each item in the inventory should have a count.
;;
;; Remember original inventory schema had only sku, type,
;; colour and size
;;
;; {:db/ident :inv/sku
;;  :db/valueTdtype :db.type/string
;;  ....}
;;
;;  {:db/ident :inv/type
;;   :db/valueType :db.type/ref
;;   .... }
;;
;;  {:db/ident :inv/colour
;;   :db/valueType :db.type/ref
;;   .... }
,, 
;;  {:db/ident :inv/size
;;   :db/valueType :db.type/ref
;;   .... }
;;
;; Now we add:
;; {:db/ident :inv/count
;;  :db/vvalueType :db.type/long
;; ---------------------------------------------------------------------
(def inv-counts-schema
  [{:db/ident :inv/count
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/one}])


(defn assert-count-schema!
  "Transact the count inventory schema"
  [conn]
  (d/transact conn inv-counts-schema))

;; ---------------------------------------------------------------------
;;
;; Now we can assert that we have seven of SKU-3, SKU-7 and SKU-10:
;;
;; Note we are using lookup refs [unique attr val] to identify the entities:
;;  A lookup ref is a two element list of unique  +  that uniquely
;;  identifies an entity, e.g.
;; ---------------------------------------------------------------------
(def inventory-count-data
  [[:db/add [:inv/sku "SKU-3"] :inv/count 3]
   [:db/add [:inv/sku "SKU-7"] :inv/count 27]
   [:db/add [:inv/sku "SKU-9"] :inv/count 9]])

;; ---------------------------------------------------------------------
;;
;; Now we can assert that we have seven of SKU-21 and a thousand of SKU-42: 
;; ---------------------------------------------------------------------

(defn assert-count-data!
  "Transact the count inventory data"  
  [conn]
  (d/transact conn inventory-count-data))


;; ---------------------------------------------------------------------
;; Accumulation: add inventory count schema and seed data  
;; ---------------------------------------------------------------------
(defn setup-counts!
  "Assert inventory counts schema and seed"
  [conn]
  (let [res-schema (assert-count-schema! conn)
        res-data (assert-count-data! conn)]
    @res-data))


(defn setup-counts-if-not-exist!
  [conn]
  (if (some? conn)
    (setup-counts! conn)
    (let [res (setup-db!)
          conn (:connection res)]
      (setup-counts! conn))))


;; ---------------------------------------------------------------------
;; Query for SKUs with counts 
;; ---------------------------------------------------------------------
(def inv-count-q
  '[:find ?e ?sku ?cnt
    :where [?e :inv/sku ?sku]
           [?e :inv/count ?cnt]])

;; ---------------------------------------------------------------------
;; Query should return the SKUs 3 7 and 9 with corresponsing counts
;; #{[17592186045437 "SKU-7" 27]
;;   [17592186045439 "SKU-9" 9]
;;   [17592186045433 "SKU-3" 3]}
;; ---------------------------------------------------------------------
(defn inv-count
  "Return inventory of SKUs and count"
  [conn]
  (d/q inv-count-q (d/db conn)))




;; ---------------------------------------------------------------------
;; Correct SKU-9. Count was not correct
;;
;; [:db/retract lookup ref arribute val]
;; ---------------------------------------------------------------------

(def count-sku9-q
  [[:db/retract [:inv/sku "SKU-9"] :inv/count 9]
   [:db/add "datomic.tx" :db/doc "retract and correct count for SKU-3"]])

(defn retract-count-sku9
  [conn]
  (d/transact conn count-sku9-q ))

;; ---------------------------------------------------------------------
;; Impliciit retract
;;
;; Count of SKU-3 mut be 1
;; We don't need to explicitly retract and then assert a new value
;; Since the cardinality is :cardinality/one we simply asser t the new
;; value. Datomic know that and will autmatically retract the old value
;; before asserting the new one
;; ---------------------------------------------------------------------

(def correct-count-sku3-q
  [[:db/add [:inv/sku "SKU-3"] :inv/count 1]
   [:db/add "datomic.tx" :db/doc "corrected count to 1"]])



(defn correct-count-sku3
  [conn]
  (d/transact conn correct-count-sku3-q))



;; ---------------------------------------------------------------------
;; 8. History
;;
;; Datomic can provides 2 types of historical querries:
;; - as-of - any previous point in time. Time is either as an instant or
;;           a transaction id.
;; 
;; - history - entire history of your data
;;
;; ---------------------------------------------------------------------

;; ---------------------------------------------------------------------
;; 7.1 as-of querries
;; You don't need to remember the exact instant in time.
;; You can query the system the most recent transactions instead
;; and use those:
;; [?tx :db/txInstant]
;; ---------------------------------------------------------------------
(def last-3-tx-q '[:find (max 3 ?tx)
                   :where [?tx :db/txInstant]])

(defn last-3-tx
  "Get last 3 transaction ids.
  Returns a list of a list of a list of 3 tx ids
  [[[13194139534393 13194139534392 13194139534391]]]"
  [conn]
  (d/q last-3-tx-q (d/db conn)))



(defn last-3-tx-sorted
  "Get last 3 transaction ids.
  Returns a list of a list of a list of 3 tx ids
  [[[13194139534393 13194139534392 13194139534391]]]

  NOTE: datomic-free returns unsorted hashset
  #{[13194139534325] [13194139533366] ...}
  Needs sorting: sort-by "
  [conn]
  (->> (d/q last-3-tx-q (d/db conn))
       (sort-by first)))


;; ---------------------------------------------------------------------
;; The earliest of last 3 Tx
;; ---------------------------------------------------------------------
(defn first-of-last-3-tx
  "Get last 3 transactions and return earliest meaning
   leasr of the Tx ids
   :eg 13194139534391"
  [conn]
  (->> (last-3-tx-sorted conn)
       (first)
       (first)
       (last)))

;; ---------------------------------------------------------------------
;; Qerry count as-of tx
;; Runds querry against (def db-as-of (d/as-of tx))
;; ---------------------------------------------------------------------
(defn inv-count-as-of
  "Runs inventory count querry as-of (point in time point of) tx"
  [conn tx]
  (let [db (d/db conn)
        db-before (d/as-of db tx)] 
    (d/q inv-count-q db-before)))


;; ---------------------------------------------------------------------
;; History query for inventory count asserstions and reractions
;; ---------------------------------------------------------------------
(def history-q '[:find ?e ?sku ?cnt ?tx ?op
                 :where [?e :inv/count ?cnt ?tx ?op]
                 [?e :inv/sku ?sku]])


(defn history-of-count
  "Querry the history of the count assertions and restractions" 
  [conn]
  (let [db (d/db conn)
        db-hist (d/history db)]
    (d/q history-q db-hist)))
