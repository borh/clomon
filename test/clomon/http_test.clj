(ns clomon.http-test
  (:require [clomon.http :refer :all]
            [clojure.test :refer :all]))

(deftest url-check-test
  (is (:success (url-check "https://google.com" {:check-fn (expect-status 200)})))
  (is (= "host is null: na.bor.space" (:status (url-check "na.bor.space" {})))))
