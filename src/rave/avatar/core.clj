(ns rave.avatar.core
  (:require [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [ring.middleware.multipart-params.byte-array :refer [byte-array-store]]
            [ring.middleware.reload :refer [wrap-reload]]
            [compojure.core :refer [GET POST defroutes]]
            [compojure.route :refer [not-found]]
            [clojure.data.json :as json]
            [taoensso.timbre :refer [info debug]]
            [clojure.spec.alpha :as s]
            [fipp.edn :refer [pprint]]))

;; ----- CONFIGURATION -----
(def image-path "/images")
(def image-sizes {:small {}
                  :med   {}
                  :large {}})

;; Note: Consider using a nano-id (https://github.com/zelark/nano-id).
;; for now sticking with regular uuid per spec.
(defn uuid [] (str (java.util.UUID/randomUUID)))

(defonce images
  (atom {}))

;; Note: If behind reverse proxy, may need to get proper host and
;; protocol from request headers.  e.g., X-Forwarded-Proto or similar.
(defn image-url
  "Returns the full URL of an image."
  [req id size]
  (str (-> req :scheme name)
       "://"
       (get-in req [:headers "host"])
       image-path
       "/"
       id
       "-"
       (name size)
       ".jpg"))

(defn image-urls
  "Returns a map of all urls for an images with id."
  [req id]
  (reduce (fn [m size]
            (assoc m size (image-url req id size)))
          {}
          (keys image-sizes)))

(defn list-images
  "Returns a json response listing image urls for id if specified or all images otherwise."
  ([req id]
   {:headers {"Content-type" "application/json"}
    :status  200
    :body    (json/write-str {:images (image-urls req id)})})
  ([req]
   {:headers {"Content-type" "application/json"}
    :status  200
    :body    (json/write-str {:images
                              (reduce (fn [m id]
                                        (assoc m id (image-urls req id)))
                                      {}
                                      (keys @images))})}))

(defn process-upload
  [req]
  #_ (debug req)
  (pprint req)

  ;; TODO: validate request
  (let [{:keys [content-type bytes]} (get-in req [:multipart-params "image"])
        id                           (uuid)]
    ;; TODO: process images
    (swap! images assoc id {:orig [content-type bytes]})
    (list-images req id)))

(defroutes app
  (GET  "/"       [] "Image server home page")
  (GET image-path [] list-images)
  (POST "/upload" [] (-> process-upload
                         ;; Store uploads in memory instead of temp file per requirement.
                         (wrap-multipart-params {:store (byte-array-store)})))
  (not-found "<h1>Page not found.</h1>"))

(def reloadable-app
  (wrap-reload #'app))

(defn -main
  [& _args]
  (info "Starting server")
  (run-jetty reloadable-app {:port  9090
                  :join? false}))
#_ (-main)
