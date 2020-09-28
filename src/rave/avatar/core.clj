(ns rave.avatar.core
  (:require [ring.adapter.jetty :refer [run-jetty]]
            [taoensso.timbre :refer [info]]))


(defn hello-handler [_request]
  {:status  200
   :headers {"Content-Type" "text/html"}
   :body    "Hello World"})

(defn -main
  [& _args]
  (info "Starting server")
  (run-jetty hello-handler {:port  3000
                            :join? false}))
