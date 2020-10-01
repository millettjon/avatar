(ns rave.avatar.db)

;; Image store.
(defonce images
  (atom {}))
#_ (reset! images {})
