(ns clomon.utils
  (:require
   [clojure.core.memoize :as memoize]
   [tick.alpha.api :as t]
   [camel-snake-kebab.core :as csk]))

(def dashes->underscores
  (memoize/fifo csk/->snake_case_string {} :fifo/threshold 512))

(def underscores->dashes
  (memoize/fifo csk/->kebab-case-keyword {} :fifo/threshold 512))

(defmacro with-time-elapsed
  [expr]
  `(let [start# (t/now)
         ret# ~expr
         elapsed# (t/between start# (t/now))]
     (assoc ret# :ts start# :duration elapsed#)))
