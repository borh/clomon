(ns clomon.main
  (:gen-class)
  (:require
    [mount.core :as mount]
    [taoensso.timbre :as timbre]
    [clomon.config :refer [config]]
    [clomon.db :as db]
    [clomon.ping :as ping]
    [clomon.http :as http]
    [clomon.mail :as mail]
    [clomon.wol :as wol]
    [clomon.schedule :as schedule]
    [clomon.server :as server]))

(defmulti run :type)

(defn outage-hook [m id]
  (let [outage-id (some-> (db/find-open-outage db/connection (assoc m :id id)) :begin-id)]
    (if (:success m)
      ;; Succeeded so end outage if already exists.
      (if outage-id
        (db/end-outage! db/connection {:begin-id outage-id :end-id id}))
      ;; Failed so start outage if none open.
      (if-not outage-id
        (db/start-outage! db/connection {:begin-id id})))))

(defmethod run :http
  [{:keys [url status poll-interval group-name name]
    :or   {poll-interval (* 60 5)
           status        200
           group-name    (str url "@" poll-interval)}
    :as   opts}]
  (schedule/run-monitor
    poll-interval
    0
    (fn []
      (let [m (assoc (http/url-check url (merge {:check-fn (http/expect-status status)} opts))
                :monitor-type :http
                :group-name group-name
                :name name)
            id (some-> (db/insert! :monitors m) first :id)]
        (timbre/info m)))))

(defmethod run :ping
  [{:keys [host poll-interval timeout group-name name]
    :or   {poll-interval (* 60 1)
           timeout       1000
           group-name    (str host "@" poll-interval)}}]
  (schedule/run-monitor
    poll-interval
    0
    (fn []
      (let [m (assoc (ping/ping host timeout)
                :monitor-type :ping
                :group-name group-name
                :name name)
            id (some-> (db/insert! :monitors m) first :id)]
        (timbre/info m id)
        (outage-hook m id)))))

(defmethod run :wol
  [{:keys [host mac broadcast poll-interval delay]
    :or   {poll-interval (* 60 60)
           broadcast     "255.255.255.255"}}]
  (schedule/run-monitor
    poll-interval
    delay
    (fn []
      (timbre/info (str "wol " host " " mac))
      (wol/wake-by-mac mac broadcast))))

(defn -main
  [& args]
  (mount/start)
  (timbre/info config)
  (doseq [monitor (map (fn [m]
                         (if (:name m)
                           m
                           (assoc m :name ((case (:type m)
                                             :ping :host
                                             :http :url
                                             :wol :host) m))))
                       (:clomon/targets config))]
    (timbre/info monitor)
    (run monitor)))

#_(-main)
#_(mount/stop)
