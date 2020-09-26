(ns datomic-tutorial.commands
  (:require [datomic.api :as d]
            [datomic-tutorial.core :a c]))


(comment
  ;; Load name apace
  (require '[datomic.api :as d])
  (require :reload '[datomic-tutorial.core :as c])

  ;;-------------------------------------------------------------
  ;;Inventory:  set up db connection and assert inventory schema + data
  (def res-setup (c/setup-db!))
  

  ;; 3. DB initialised and inventory data seeded
  ;; We can now run querries to see the inventory
  (->> (c/same-colour "SKU-3" (:connection @c/connection))
       (sort-by first))

  

  ;;-------------------------------------------------------------  
  ;; 4. Order: assert order schema
  (def res-orderschema (c/assert-order-schema!
                        (:connection @c/connection) c/order-schema))

  ;; Assert order data
  (def res-orderdata (c/assert-order-data! (:connection @c/connection) c/order-data))

  ;; Setup db in one step
  (def res-setup (c/setup-orders-if-not-exist! nil))

  ;; List orders
  (c/all-orders (:connection @c/connection))

  ;; items in the same order as...
  (c/related-items (:connection @c/connection) "SKU-7")

  (c/related-items (:connection @c/connection) "SKU-3")

  ;;-----------------------------------------------------------
  ;; 6. Retracts
  ;; sets up sku-3, 7 and 9 counts
  ;;-----------------------------------------------------------
  (def res-cntsetup (c/setup-counts-if-not-exist! nil))
  
  ;; Check
  (c/inv-count (:connection @c/connection))
  ;; #{[17592186045437 "SKU-7" 27] [17592186045439 "SKU-9" 9] [17592186045433
  ;;   "SKU-3" 3]}

  ;; Now retract assertion about SKU-9 count
  (def sku9-res (c/retract-count-sku9 (:connection @c/connection)))

  ;; check that sku-9 count was retracted
  (c/inv-count (:connection @c/connection))
  ;; #{[17592186045437 "SKU-7" 27] [17592186045433 "SKU-3" 3]}

  ;; And correct sku-3 count
  (def sku3-res (c/correct-count-sku3 (:connection @c/connection)))

  ;; Check sku-3 count is 1
  (c/inv-count (:connection @c/connection))
  ;; #{[17592186045433 "SKU-3" 1] [17592186045437 "SKU-7" 27]}


  ;;-----------------------------------------------------------
  ;; 7. History
  ;; Get last 3 transactions and the earliest of thos
  ;;-----------------------------------------------------------
  ;; (def res-3-tx (c/last-3-tx (:connection @c/connection)))
  (def txid (c/first-of-last-3-tx (:connection @c/connection)))

  ;; Inventory count as of txid
  ;; Count for SKU-3 should be 3 at that time
  (c/inv-count-as-of (:connection @c/connection) txid)

  ;; Display history of assertions and retractions
  (def hist (c/history-of-count (:connection @c/connection)))

  )
