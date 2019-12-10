(ns clomon.http
  (:require
   [org.httpkit.client :as http]
   [clomon.utils :refer [with-time-elapsed]]
   [clojure.string :as str]))

(defn expect-status [status]
  (fn [response]
    (= status (:status response))))

(defn expect-body-re [re]
  (fn [response]
    (some? (re-find re (:body response)))))

(defn expect-body [s]
  (fn [response]
    (str/includes? (:body response) s)))

(defn http-get
  [url {:keys [keepalive timeout]
        :or {keepalive -1
             timeout 5000}
        :as opts}]
  (let [m (select-keys (with-time-elapsed @(http/get url opts))
                       [:body :status :error :ts :duration])]
    (cond-> m
      (:error m) (assoc :status (let [error-message (.getMessage (:error m))]
                                  (if (re-seq #"Temporary failure in name resolution" error-message)
                                    :dns-error
                                    :network-error))
                        :success false
                        :duration nil)
      true (dissoc :error))))

(defn url-check
  ([url {:keys [check-fn]
         :or {check-fn (expect-status 200)}
         :as opts}]
   (let [m (assoc (http-get url opts) :target url)]
     (dissoc
      (if (contains? m :success) ;; Currently having :success key means it failed.
        m
        (assoc m :success (check-fn m)))
      :body))))
