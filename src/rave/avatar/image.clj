(ns rave.avatar.image
  "Image processing functions."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.spec.alpha :as s]
            [rave.avatar.config :as config]
            [rave.avatar.db :as db]
            [mikera.image.core :as img])
  (:import [java.io ByteArrayOutputStream]
           [java.awt.image BufferedImage]
           [java.awt Color]
           [javax.imageio ImageIO IIOImage ImageWriter ImageWriteParam]
           [javax.imageio.stream MemoryCacheImageOutputStream]
           [org.imgscalr Scalr]
           [com.luciad.imageio.webp WebPWriteParam]))

;; References:
;; - ImageIO: https://docs.oracle.com/javase/7/docs/api/javax/imageio/package-summary.html
;; - scaling: http://javadox.com/org.imgscalr/imgscalr-lib/4.2/org/imgscalr/Scalr.html
;; - webp:    https://github.com/sejda-pdf/webp-imageio

;; Supported image types.
(s/def ::image-type (into #{} (keys config/convert-to)))

(defn format->type
  "Converts a ImageIO image format name to the corresponding mime-type (as namespaced keyword)."
  [format]
  (->> format str/lower-case (keyword "image")))
#_ (format->type "GIF")

(defn read-image
  "Reads an image from input and returns a map with the image's format
  and BufferedData. Input can be a filename, File, or InputStream."
  [input]
  (let [iis       (-> input io/input-stream ImageIO/createImageInputStream)
        readers   (-> iis ImageIO/getImageReaders iterator-seq)
        reader    (first readers)
        mime-type (-> reader
                      .getFormatName
                      format->type)
        _         (.setInput reader iis)
        img       (.read reader 0)]
    {:mime-type mime-type
     :image     img}))
#_ (read-image "test/dog.png")

(defn supports-alpha?
  "Returns true if image type supports an alpha channel."
  [mime-type]
  (case mime-type
    :image/jpeg false
    :image/webp true))

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

(defn resize
  "Resizes an image to the specified size maintaining the aspect ratio."
  ^BufferedImage [^BufferedImage image size]
  (Scalr/resize image
                org.imgscalr.Scalr$Method/BALANCED
                size
                nil))

(defmulti convert
  (fn [_image mime-type _os _quality]
    mime-type))

(defmethod convert :image/jpeg
  [image _mime-type os quality]
  (img/write image os "jpg" :quality quality)
  os)

(defmethod convert :image/webp
  [image _mime-type os quality]
  (let [^ImageWriter writer (-> "image/webp"
                                ImageIO/getImageWritersByMIMEType
                                .next)
        param               (-> writer .getLocale WebPWriteParam.)
        compression         (-> param .getCompressionTypes (nth WebPWriteParam/LOSSY_COMPRESSION))]

    ;; Configure parameters.
    (doto param
      (.setCompressionMode ImageWriteParam/MODE_EXPLICIT)
      (.setCompressionType compression)
      (.setCompressionQuality quality))

    ;; Write it passed output stream.
    (let [mcos (MemoryCacheImageOutputStream. os)]
      (.setOutput writer mcos)
      (.write writer nil (IIOImage. image nil nil) param)
      (.close mcos)
      os)))

;; Note: Consider using a nano-id (https://github.com/zelark/nano-id).
;; for now sticking with regular uuid per spec.
(defn uuid [] (str (java.util.UUID/randomUUID)))

(defn import-image
  "Imports a source image by processing it into configured images
  sizes and saving those in the database. Returns a uuid."
  [src]
  (let [target-type (-> src :mime-type config/convert-to)
        orig        (let [img (:image src)]
                      (if (supports-alpha? target-type)
                        img
                        (remove-alpha img)))

        ;; Process source image into target sizes and convert.
        imgs (reduce (fn [m [k {:keys [px quality]}]]
                       (let [os (ByteArrayOutputStream.)]
                         (-> orig
                             (resize px)
                             (convert target-type os quality))
                         (assoc m k {:mime-type target-type
                                     :bytes (.toByteArray os)})))
                     {}
                     config/image-sizes)
        id   (uuid)]
    (swap! db/images assoc id imgs)
    {id imgs}))
