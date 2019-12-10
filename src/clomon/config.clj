(ns clomon.config
  (:require
    [mount.core :refer [defstate]]
    [aero.core :refer [read-config]]
    [clojure.spec.alpha :as s]
    [taoensso.timbre :as timbre]
    [taoensso.timbre.appenders.core :as appenders]
    [clojure.java.io :as io]))

(defstate config :start
  (merge-with
    merge
    {:clomon/reporting {:log/file  "clomon.log"
                        :log/email "root@localhost"}
     ;; :clomon/dbspec {:jdbc/url "jdbc:sqlite::memory:"}
     :clomon/config    {:config/poll-interval (* 10 1000)
                        :config/timeout       1000}}
    (read-config (io/resource "config.edn"))))

(mount.core/start)

(timbre/merge-config!
  {:appenders
   {:spit
    (appenders/spit-appender
      {:fname (get-in config [:clomon/reporting :log/file])})}})
