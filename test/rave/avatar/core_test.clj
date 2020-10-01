(ns rave.avatar.core-test
  (:require [rave.avatar.core :as core]
            [rave.avatar.config :as config]
            [rave.avatar.handler :as handler]
            [com.stuartsierra.component :as component]
            [clj-http.client :as client]
            [clojure.java.io :as io]
            [clojure.data.json :as json]
            [mikera.image.core :as img]
            [clojure.test :refer [deftest is use-fixtures]]))

;; Jetty port to use for testing.
(def port 9091)

(defn with-jetty [f]
  "Start jetty before tests and stop after."
  (let [server (-> core/http-server
                   (assoc :port port)
                   component/start)]
    (try
      (f)
      (finally (component/stop server)))))

(use-fixtures :once with-jetty)

(defn check-size
  "Checks that img is sized to a square of size pixels."
  [img size]
  (let [px (get-in config/image-sizes [size :px])
        w (.getWidth img)
        h (.getHeight img)]
    (is (<= h px))
    (is (<= w px))
    (is (or (= h px)
            (= w px)))))

(defn get-mime-type
  "Returns the mime-type (as keyword) for url based on the file extension."
  [url]
  (let [ext (->> url
                 (re-matches #".*\.([^\.]+)$")
                 second)]
    (keyword "image" (case ext
                       "jpg" "jpeg"
                       ext))))

(defn check-image
  "Downloads and checks an image."
  [size url]
  (let [{:keys [status
                body
                headers]} (client/get url {:as :byte-array})]
    (is (= 200 status))
    (is (= (-> url
               get-mime-type
               config/convert-to
               handler/mime-type->content-type)
           (get headers "Content-type")))
    (let [img (-> body io/input-stream img/load-image)]
      (check-size img size))))

(defn upload-file
  "Uploads file to the server."
  [file]
  (let [url                            (str "http://localhost:" port "/upload")
        {:keys [status body] :as resp} (client/post url
                                                    {:multipart [{:name    "image"
                                                                  :content (io/file file)}]})]
    (is (= 200 status))
    (is (= "application/json" (get-in resp [:headers "Content-type"])))
    (let [result (json/read-str body :key-fn keyword)
          images (-> result :images first second)]
      (prn "images" images)
      (doseq [size (keys config/image-sizes)]
        (check-image size (size images))))))

(deftest upload
  (upload-file "test/iroh.jpg")
  (upload-file "test/dog.png")
  (upload-file "test/lighthouse.gif")
  (upload-file "test/zissou.webp"))
