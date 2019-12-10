(ns clomon.ping-test
  (:require [clomon.ping :refer :all]
            [clojure.test :refer :all]))

(deftest ping-test
  (is (= :unknown-host (:status (ping "na.bor.space"))))
  ;; Dummy IP address. Might succeed under different environments.
  (is (not (:result (ping "10.0.254.254"))))
  (is (:success (ping "localhost")))
  (is (:success (ping "matrix.bor.space"))))
