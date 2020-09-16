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
    (reset! connection {:connection conn :resschems ress :ressdata resd})))

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
(def same-colourSKU7 '[:find ?o ?tp ?sz ?co
                       :where [?e :inv/sku "SKU-7"] ;;Find eid SKU-7
                              [?e :inv/colour ?c]  ;;Find colour of e
                              [?o :inv/colour ?c]  ;;Find orthers same colour
                              [?o :inv/type ?t]    ;;what's their type? ref
                              [?o :inv/size ?s]    ;;what's their size? ref                                 [?t :db/ident ?tp]  ;;What's the actual type
                              [?s :db/ident ?sz]   ;;What's the actual size?
                              [?c :db/ident ?co]]) ;;What's the actual colour?

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
(def order-schena [{:db/ident :order/items
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

(defn add-order-schema!
  "Accumulation of schema. Add oder and items schema"
  [conn schema]
  (d/transact conn schema))

;; ---------------------------------------------------------------------
;; Order data.
;; We use a nested entity map {order [item item ...]}
;; The top level is an order. The nested level is a list of items.
;; ---------------------------------------------------------------------
(def order-data
  {:order/items [{:item/id [:inv/sku "SKU-3"]
                  :item/count 7}
                 {:item/id [:inv/sku "SKU-17"]
                  :item/count 3}]})

(defn add-order-data!
  "Accumulation of data. Add order data"
  [conn data]
  (d/transact conn [order-data]))


