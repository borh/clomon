(ns clomon.server
  (:require
    [mount.core :as mount :refer [defstate]]
    [org.httpkit.server :as hk]

    [compojure.route :as route]
    [compojure.core :refer [defroutes context GET]]
    [ring.util.codec :as codec]
    [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
    [ring.middleware.webjars :refer [wrap-webjars]]

    [clomon.reporting :as reporting]
    [clomon.config :refer [config]]))

(defroutes
  main-routes
  (context (-> config :clomon/server :prefix) []
    (GET "/" [] (reporting/report))
    (GET "/target/:target/:date" [target date] (reporting/target-by-date (codec/url-decode target) date))
    (GET "/monitor/:monitor" [monitor] (reporting/monitor-summary (codec/url-decode monitor)))
    (GET "/summary/" [] (reporting/outage-summary))
    (route/resources "/")
    (route/not-found "Page not found"))
  (route/not-found "Not Found"))

(def app
  (-> main-routes
      (wrap-webjars (str (-> config :clomon/server :prefix) "/assets"))
      (wrap-defaults site-defaults)))

(defn start-server []
  (when-let [server (hk/run-server app (merge {:port 8080 :join? false}
                                              (get config :clomon/server)))]
    (println "Server has started!")
    server))

(defstate my-server
  :start (start-server)
  :stop (my-server :timeout 100))

#_(mount/start)

#_(defroutes all-routes
             (GET "/" [] show-landing-page)
    (files "/static/")                                      ;; static file url prefix /static, in `public` folder
    (not-found "<p>Page not found.</p>"))                   ;; all other, return 404
