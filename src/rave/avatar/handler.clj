(ns rave.avatar.handler
  (:require [clojure.data.json :as json]
            [rave.avatar.config :as config]
            [rave.avatar.db :as db]
            [rave.avatar.image :as img]
            [clojure.string :as str]
            [clojure.spec.alpha :as s]
            [hiccup.core :refer [html]]
            [hiccup.element :as h]))

;; --------------------------------------------------
;; EXCEPTION HANDLING
;; --------------------------------------------------

(defn validate
  "Checks x using spec. If x is valid? returns x. Otherwise throws an ex-info with msg."
  [x spec msg]
  (if (s/valid? spec x)
    x
    (throw (ex-info (s/explain-str spec x) {:status 400
                                            :user-msg msg}))))

(defn wrap-exception [handler]
  (fn [request]
    (try (handler request)
         (catch Exception e
           {:status 500
            :body "Exception caught"}))))

;; --------------------------------------------------
;; LIST IMAGES
;; --------------------------------------------------

;; Note: If behind reverse proxy, may need to get proper host and
;; protocol from request headers.  e.g., X-Forwarded-Proto or similar.
;; Alternatively, the proxy could rewrite the urls.
(defn image-url
  "Returns the full URL of an image."
  [req id size extension]
  (str (-> req :scheme name)
       "://"
       (get-in req [:headers "host"])
       "/images/"
       id
       "-"
       (name size)
       "."
       extension))

(defn mime-type->extension
  "Returns a file extension for the given mime type."
  [mime-type]
  (let [ext (name mime-type)]
    (case ext
        "jpeg" "jpg"
        ext)))

(defn image-urls
  "Returns a map of urls by image size for an image id."
  [req id imgs]
  (reduce (fn [m size]
            (let [extension (-> imgs size :mime-type mime-type->extension)]
              (assoc m size (image-url req id size extension))))
          {}
          (-> config/image-sizes keys)))

(defn list-images
  "Returns a json response listing image urls for id if specified or all images otherwise."
  ([req]
   (list-images req @db/images))
  ([req images]
   {:headers {"Content-type" "application/json"}
    :status  200
    :body    (json/write-str {:images
                              (reduce (fn [m [id imgs]]
                                        (assoc m id (image-urls req id imgs)))
                                      {}
                                      images)})}))

;; --------------------------------------------------
;; SERVE SINGLE IMAGE
;; --------------------------------------------------

;; Regex to parse image url into uuid and size.
(def image-path-re
     (re-pattern (str "([0-9a-f\\-]+)-("
                      (->> config/image-sizes
                           keys
                           (map name)
                           (str/join "|"))
                      ").+")))

(defn image-path->map
     "Takes an image path and returns a map with image :id and :size."
     [path]
     (when-let [[_ id size] (re-matches image-path-re path)]
       {:id id
        :size (keyword size)}))
#_ (image-path->map "cc572b5b-995b-4a5b-9ba2-b2d444c06638-small.jpg")
#_ (image-path->map "cc572b5b-995b-4a5b-9ba2-b2d444c06638-huge.jpg")


(defn mime-type->content-type
  [mime-type]
  (str (namespace mime-type) "/" (name mime-type)))

(defn serve-image
  "Serves an image. The request path must have a valid uuid and size."
  [req]
  (when-let [{:keys [id size]} (image-path->map (get-in req [:params :image]))]
    (when-let [{:keys [mime-type bytes]} (get-in @db/images [id size])]
      {:headers {"Content-type" (mime-type->content-type mime-type)}
       :status  200
       :body    bytes})))

;; --------------------------------------------------
;; IMAGE GALLERY
;; --------------------------------------------------

(defn gallery
     "Minimal gallery page to display all images."
  [req]
  {:status  200
   :body    (html [:div
                   [:h1 "gallery"]
                   (map (fn [[id imgs]]
                          [:div
                           id
                           (map (fn [[_k url]]
                                  (h/image url))
                                (image-urls req id imgs))])
                        @db/images)])})

;; --------------------------------------------------
;; UPLOAD IMAGE
;; --------------------------------------------------
(defn upload
  [req]
  (let [bytes  (get-in req [:multipart-params "image" :bytes])
        src    (img/read-image bytes)
        _      (validate (:mime-type src) ::img/image-type "Unsupported image type.")
        images (img/import-image src)]
    (list-images req images)))
