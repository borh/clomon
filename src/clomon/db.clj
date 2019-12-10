(ns clomon.db
  (:require
   [mount.core :refer [defstate]]
   [clomon.config :refer [config]]
   [clomon.utils :as utils]
   [taoensso.timbre :as timbre]
   [tick.alpha.api :as t]

   [hikari-cp.core :as cp]
   [clojure.java.jdbc :as j]
   [hugsql.core :as hugsql]
   [hugsql-adapter-case.adapters :refer [kebab-adapter]])
  (:import [org.postgresql.util PGInterval]))

(hugsql/def-db-fns "clomon/sql/database.sql" {:adapter (kebab-adapter)})

(extend-protocol j/ISQLValue
  clojure.lang.Keyword
  (sql-value [v]
    (name v))

  java.time.Instant
  (sql-value [v]
    (java.sql.Timestamp/valueOf (t/date-time v))
    #_(java.sql.Timestamp. (.getMillis v)))

  java.time.Duration
  (sql-value [v]
    (.toMillis v)
    #_(PGInterval. 0 0 (t/days v) (t/hours v) (t/minutes v) ^double (+ (t/seconds v) (/ (t/millis v) 1000)))))

(defstate ^{:on-reload :noop}
  connection
  :start {:datasource (cp/make-datasource
                       (merge {:adapter "postgresql"
                               :re-write-batched-inserts true}
                              (select-keys (:clomon/dbspec config)
                                           [:database-name :username :password])))}
  :stop (cp/close-datasource (:datasource connection)))

(defn drop-all-cascade! []
  (let [q ["SELECT 'DROP TABLE \"' || tablename || '\" CASCADE' FROM pg_tables WHERE schemaname = 'public'"]
        drop-stmts (->> (j/query connection q)
                        (map vals)
                        flatten)]
    (doseq [stmt drop-stmts]
      (j/db-do-commands connection stmt))))

;; ^{:on-reload :noop}
;; TODO create database!
(defstate database-init
  :start
  (let [existing-tables (tables-list connection)]
    (cond
      (= 0 (count existing-tables))
      (do (timbre/info "Creating tables.")
          (create-monitors-table! connection))

      (get-in config [:clomon/dbspec :db/clean])
      (do (timbre/warn "Recreating tables.")
          (drop-all-cascade!)
          (create-monitors-table! connection))

      :else (timbre/info "Using existing tables."))
    :started))

(defn insert! [table x]
  (j/insert! connection table x {:entities utils/dashes->underscores}))

(defn insert-multi! [table xs]
  (j/insert-multi! connection table xs {:entities utils/dashes->underscores}))

(defn query [q]
  (j/query connection [q]
           {:identifiers utils/underscores->dashes
            :row-fn (fn [m]
                      (cond-> m
                        (:monitor-type m) (update :monitor-type keyword)
                        (:ts m) (update :ts t/date-time)
                        (:duration m) (update :duration #(java.time.Duration/ofMillis %))))}))

(defn monitor-report
  ([]
   (summarize connection))
  ([{:keys [:from-date :to-date] :as params}]
   (summarize-from-date connection params)))

(defstate cached-reports
  :start (monitor-report {:to-date (t/yesterday)}))

(defstate outages
  :start (get config :clomon/outages))

#_(mount.core/start)
