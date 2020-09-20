(ns datomic-tutorial.core
  (:require [datomic.api :as d]))

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
;;
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
;; Generate a combination of maps from 4, types,
;; 4 colours and 4 sizes.
;; => 4 x 4 x 4 = 64 maps
;;-------------------------------------------------------------
(defn sample-data
  "Generate sample data
  64 maps:
  {:inv/sku \"SHU-1\"
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
;; Create database
;; and connect
;;-------------------------------------------------------------
(defn create-db!
  "Create and connect to a database
   with the specied URI returing the
  connection"
  [dburi]
  (d/create-database dburi)
  (d/connect dburi))

;;-------------------------------------------------------------
;; Required only during REPL sessions
;;-------------------------------------------------------------
(defn delete-db!
  "Clean up database"
  []
  (try
    (d/delete-database db-uri)
    (reset! connection nil)
    (catch Exception e
        (str "::-> delete-db! failed: " (.getMessage e)))))

;;-------------------------------------------------------------
;; Seed database with test data
;;-------------------------------------------------------------
(defn setup-db!
  "Initialise databae"
  []
  (let [schema (read-EDN "resources/db/schema.edn")
        data (sample-data)
        conn (create-db! db-uri)
        ress @(d/transact conn schema)
        resd @(d/transact conn data)]
    (reset! connection {:connection conn :resschema ress :resdata resd})))

;; ---------------------------------------------------------------------
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

;; Query all SKUs
(def all-skus-q '[:find ?e ?sku
                  :where [?e :inv/sku ?sku]])

;; Find SKUs with same colour as SKU-7
;; Return their entity IDs, type, size and colour
;;What's the actual colour?
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
;; Accumulate: track orders as well
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
;; Order data.
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
;; Task: retriev all order and number of items
;;
;; --------------------------------------------------------------------
(def all-orders-q '[:find ?e ?sku ?cnt
                    :where [?e :order/items   ?eit]   ;; find all order's item eids
                           [?eit :item/id     ?esk]   ;; with the eids find the SKU ids 
                           [?esk :inv/sku     ?sku]   ;; with the SKU ids find the SKU
                           [?eit :item/count  ?cnt]]) ;; with eids find the count,

(defn all-orders
  "Query all orders"
  [conn]
  (d/q all-orders-q (d/db conn)))


;; ---------------------------------------------------------------------
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
;; If you know a unique attribute they dont need to know the entity id.
;; A lookup ref will do the trick.
;;
;; A lookup ref is a two element list of unique attribute + value uniquely
;; identifies an entity [:inv/sku  "SKU-7"]
;; ---------------------------------------------------------------------
(defn related-items
  "Query all orders. Uses a query ref "
  [conn sku]
  (d/q related-items-q (d/db conn) [:inv/sku sku]))


;; Rules
(def rules
  '[[(ordered-together ?inv ?orther-inv)
     [?item  :item/id ?inv]
     [?order :order/items ?item]
     [?order :order/items ?other-item]
     [?other-item :item/id ?other-inv]]])

(def related-items-q2 '[:find ?sku
                        :in $ ?inv
                        :where (ordered-together ?inv other-inv)
                               [?oher-inv :inv/sku ?sku]])


(defn related-items2
  [conn sku]
  (d/q related-items-q2 (d/db conn) [:inv/sku sku]))
