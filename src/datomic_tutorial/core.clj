(ns datomic-tutorial.core
  (:require [datomic.api :as d]))

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
        res @(d/transact conn schema)]
    {:connection conn :results res} ))



;; Datomic maintains the entire history of your data.
;; From this, you can query against a database value
;; as of a particular point in time.
;; 
;; The db API returns the latest database value from
;; a connection.
(defn same-colour
  [sku conn]
  (d/q '[:find ?sku ?c ?t
         :where [?e  :inv/sku sku]
                [?e  :inv/colour ?c]
                [?e2 :inv/colour ?c]
                [?e2 :inv/sku ?sku]
                [?e2 :inv/type ?t] ]
       (d/db conn)))
