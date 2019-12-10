(ns clomon.schedule
  (:require
   [overtone.at-at :as at]
   [mount.core :refer [defstate]]))

(defstate pool
  :start (at/mk-pool)
  :stop (.shutdownNow ^java.util.concurrent.ScheduledThreadPoolExecutor (:thread-pool @(:pool-atom pool))))

(defn run-monitor
  [poll-interval delay task-fn]
  (at/every (* 1000 poll-interval) task-fn pool :initial-delay delay)
  :started)
