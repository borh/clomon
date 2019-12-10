(ns clomon.db-test
  (:require [clomon.db :refer :all]
            [clojure.test :refer :all]
            [tick.alpha.api :as t]
            [mount.core :as mount]
            [clomon.db :as db]
            [clojure.java.io :as io]))

(defn mount-fixture [f]
  (mount/start-with {#'clomon.config/config (aero.core/read-config (io/resource "test.edn"))})
  (mount/start #'clomon.db/database-init #'clomon.db/connection)
  (f)
  (mount/stop))

(use-fixtures :once mount-fixture)

(deftest schema-exists-test
  (is (= #{"monitors" "outages"}
         (set (map :table-name (tables-list connection))))))

(deftest query-test
  (let [http-record {:monitor-type :http :group-name "http-test" :name "Google(HTTPS)" :target "https://www.google.com/" :status "200" :success true :ts (t/date-time "2019-02-01T06:00:00") :duration (t/new-duration 1 :seconds) :id 1}
        ping-record {:monitor-type :ping :group-name "ping-test" :name "Google(8.8.8.8)" :target "8.8.8.8" :status "ok" :success false :ts (t/date-time "2019-02-01T06:00:00") :duration (t/new-duration 100 :millis) :id 2}]
    (insert! :monitors http-record)
    (insert! :monitors ping-record)
    (is (= http-record (first (query "SELECT * FROM monitors WHERE monitor_type='http'"))))
    (is (= ping-record (first (query "SELECT * FROM monitors WHERE monitor_type='ping'"))))
    (mount/start #'clomon.db/cached-reports #'clomon.db/outages)
    (is (= 2 (count (db/monitor-report))))
    (is (= 2 (count outages)))))
