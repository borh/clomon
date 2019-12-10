(ns clomon.mail
  (:refer-clojure :exclude [send])
  (:require [postal.core :as postal]))

(defn hostname []
  (.getCanonicalHostName (java.net.InetAddress/getLocalHost)))

(defn send [from to subject body]
  (postal/send-message {:from from
                        :to to
                        :subject (str "clomon@" (hostname) ": " subject)
                        :body body}))
