(ns rave.avatar.core-test
  (:require [rave.avatar.core :as core]
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

(defn check-image
  "Downloads and checks an image."
  [size url]
  (prn size url)
  (let [{:keys [status
                body
                headers]
         :as   resp} (client/get url {:as :byte-array})]
    (is (= 200 status))
    (is (= "image/jpeg" (get headers "Content-type")))
    (let [img (-> body io/input-stream img/load-image)]
      (is (= (get-in core/image-sizes [size :px]) (.getWidth img) (.getHeight img))))))

(defn upload-file
  "Uploads file to the server."
  [file]
  (let [url                            (str "http://localhost:" port "/upload")
        {:keys [status body] :as resp} (client/post url
                                                    {:multipart [{:name    "image"
                                                                  :content (io/file file #_ "test/iroh.jpg")}]})]
    (is (= 200 status))
    (is (= "application/json" (get-in resp [:headers "Content-type"])))
    (let [{:keys [images]} (json/read-str body :key-fn keyword)]
      (doseq [size (keys core/image-sizes)]
        (check-image size (size images))))))

(deftest upload
  (upload-file "test/iroh.jpg")
  (upload-file "test/dog.png"))
