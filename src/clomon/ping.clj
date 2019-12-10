(ns clomon.ping
  (:require [clomon.utils :refer [with-time-elapsed]]))

(defn ping
  "Time an ICMP or port 7 ping to a given domain with timeout (1s default).
  If host is not reachable before timeout, a false result is
  returned. If there is a DNS query error, an :unknown-host value is
  returned instead. Note that this functionality depends on
  CAP_NET_RAW=ep capabilities being available (or superuser access)."
  ([domain]
   (ping domain 1000))
  ([domain timeout]
   (let [result
         (with-time-elapsed
           (assoc
            (if-let [addr (try (java.net.InetAddress/getByName domain)
                               (catch java.net.UnknownHostException _ false))]
              (let [reachable? (.isReachable addr timeout)]
                {:success reachable? :status (if reachable? :ok :timeout)})
              {:success false :status :unknown-host})
            :target domain))]
     (if (:success result) ;; FIXME: change to check :timeout
       result
       (assoc result :duration nil)))))
