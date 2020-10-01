(ns rave.avatar.core
  (:require [ring.component.jetty :refer [jetty-server]]
            [com.stuartsierra.component :as component]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [ring.middleware.multipart-params.byte-array :refer [byte-array-store]]
            [ring.middleware.reload :refer [wrap-reload]]
            [compojure.core :refer [GET POST defroutes]]
            [compojure.route :refer [not-found]]
            [taoensso.timbre :refer [info debug]]
            [rave.avatar.handler :as handler]
            [rave.avatar.config :as config]
            [fipp.edn :refer [pprint]]))

(defroutes app
  (GET  "/"              [] "Image server home page")
  (GET  "/images/:image" [] handler/serve-image)
  (GET  "/images"        [] handler/list-images)
  (GET  "/gallery"       [] handler/gallery)
  (POST "/upload"        [] (-> handler/upload
                                ;; Store uploads in memory instead of temp file per requirement.
                                (wrap-multipart-params {:store (byte-array-store)})))
  (not-found "<h1>Page not found.</h1>"))

(def reloadable-app
  (wrap-reload #'app))

(def http-server
  (jetty-server {:app  {:handler reloadable-app}
                 :port config/port}))

(defn -main
  [& _args]
  (info "Starting server")
  (alter-var-root #'http-server component/start))
#_ (-main)
