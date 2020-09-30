(ns rave.avatar.core
  (:require [ring.component.jetty :refer [jetty-server]]
            [com.stuartsierra.component :as component]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [ring.middleware.multipart-params.byte-array :refer [byte-array-store]]
            [ring.middleware.reload :refer [wrap-reload]]
            [compojure.core :refer [GET POST defroutes]]
            [compojure.route :refer [not-found]]
            [mikera.image.core :as img]
            [clojure.java.io :refer [input-stream]]
            [clojure.data.json :as json]
            [taoensso.timbre :refer [info debug]]
            [clojure.string :as str]
            [hiccup.core :refer [html]]
            [hiccup.element :as h]
            [fipp.edn :refer [pprint]])
  (:import [java.awt.image BufferedImage]
           [java.awt Color]
           [org.imgscalr Scalr]))

;; References
;; - http://javadox.com/org.imgscalr/imgscalr-lib/4.2/org/imgscalr/Scalr.html

;; ----- CONFIGURATION -----
(def port 9090)
(def image-sizes {:small {:px 75  :quality 0.7}
                  :med   {:px 150 :quality 0.7}
                  :large {:px 300 :quality 0.8}})

;; Note: Consider using a nano-id (https://github.com/zelark/nano-id).
;; for now sticking with regular uuid per spec.
(defn uuid [] (str (java.util.UUID/randomUUID)))

;; Image store.
(defonce images
  (atom {}))
#_ (reset! images {})

;; Note: If behind reverse proxy, may need to get proper host and
;; protocol from request headers.  e.g., X-Forwarded-Proto or similar.
;; Alternatively, the proxy could rewrite the urls.
(defn image-url
  "Returns the full URL of an image."
  [req id size]
  (str (-> req :scheme name)
       "://"
       (get-in req [:headers "host"])
       "/images/"
       id
       "-"
       (name size)
       ".jpg"))

(defn image-urls
  "Returns a map of urls by image size for an image id."
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

(defn resize
  "Resizes an image to the specified size maintaining the aspect ratio."
  ^BufferedImage [^BufferedImage image size]
  (Scalr/resize image
                org.imgscalr.Scalr$Method/BALANCED
                size
                nil))

(defn remove-alpha
  "Returns a copy of img with the alpha channel removed."
  [img]
  (let [width (.getWidth img)
        height (.getHeight img)
        target (BufferedImage. width
                               height
                               BufferedImage/TYPE_INT_RGB)]
    ;; TODO: fix reflection warnings
    (doto (.createGraphics target)
      (.setColor Color/WHITE) ; TODO: customize background?
      (.fillRect 0 0 width height)
      (.drawImage img 0 0 nil)
      .dispose)
    target))

(defn process-upload
  [req]
  ;; TODO: validate request
  (let [{:keys [content-type bytes]} (get-in req [:multipart-params "image"])
        id                           (uuid)]
    (let [orig (-> bytes input-stream img/load-image)

          ;; Process source image into target sizes.
          imgs (reduce (fn [m [k {:keys [px quality]}]]
                         (let [os (java.io.ByteArrayOutputStream.)]
                           (-> orig
                               remove-alpha ; returns a copy w/ no alpha channel
                               ;;(img/resize 100)
                               (resize px)
                               (img/write os "jpg" :quality quality))
                           (assoc m k ["image/jpeg" (.toByteArray os)])))
                       {}
                       image-sizes)]
      (swap! images assoc id imgs)
      #_ (img/show orig)
      #_ (prn "hasAlpha" (-> orig .getColorModel .hasAlpha))
      #_ (img/write (remove-alpha orig) "foo.jpg" "jpg" :quality 0.7)
      #_ (img/write orig "foo.png" "png" :quality 0.7)
      )
    (list-images req id)))

;; Regex to parse image url into uuid and size.
(def image-path-re
  (re-pattern (str "([0-9a-f\\-]+)-("
                   (->> image-sizes
                        keys
                        (map name)
                        (str/join "|"))
                   ").jpg")))

(defn image-path->map
  "Takes an image path and returns a map with image :id and :size."
  [path]
  (when-let [[_ id size] (re-matches image-path-re path)]
    {:id id
     :size (keyword size)}))
#_ (image-path->map "cc572b5b-995b-4a5b-9ba2-b2d444c06638-small.jpg")
#_ (image-path->map "cc572b5b-995b-4a5b-9ba2-b2d444c06638-huge.jpg")

(defn serve-image
  "Serves an image. The request path must have a valid uuid and size."
  [req]
  (when-let [{:keys [id size]} (image-path->map (get-in req [:params :image]))]
    (when-let [[content-type bytes] (get-in @images [id size])]
      {:headers {"Content-type" content-type}
       :status  200
       :body    bytes})))

(defn gallery
  "Minimal gallery page to display all images."
  [req]
  {:headers {#_#_"Content-type" "application/json"}
   :status  200
   :body    (html [:div
                   [:h1 "gallery"]
                   (map (fn [id]
                          [:div
                           id
                           (map (fn [[_k url]]
                                  (h/image url))
                                (image-urls req id))])
                        (keys @images))])})

(defroutes app
  (GET  "/"              [] "Image server home page")
  (GET  "/images/:image" [] serve-image)
  (GET  "/images"        [] list-images)
  (GET  "/gallery"       [] gallery)
  (POST "/upload"        [] (-> process-upload
                                ;; Store uploads in memory instead of temp file per requirement.
                                (wrap-multipart-params {:store (byte-array-store)})))
  (not-found "<h1>Page not found.</h1>"))

(def reloadable-app
  (wrap-reload #'app))

(def http-server
  (jetty-server {:app  {:handler reloadable-app}
                 :port port}))

(defn -main
  [& _args]
  (info "Starting server")
  (alter-var-root #'http-server component/start))
#_ (-main)
