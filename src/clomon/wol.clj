(ns clomon.wol
  (:require
   [clojure.string :as str]
   [clojure.java.shell :as shell]
   [clojure.spec.alpha :as s])
  (:import [java.net InetAddress DatagramPacket DatagramSocket]))

(s/def ::mac-string
  (s/and string? #(re-seq #"(?i)^(([a-f\d]){2}[\:-]){5}([a-f\d]){2}$" %)))

(s/def ::ip-address
  (s/and string? (fn [s]
                   (every?
                    (fn [d] (<= 0
                                (try (Integer/parseInt d)
                                     (catch Exception _ -1))
                                255))
                    (str/split s #"\.")))))

(defn domain-to-mac
  "Converts given domain string into a MAC address by querying the arp
  cache. This is achieved by shelling out to the arp command, as there
  is no native Java functionality for this. Note that this will only
  work for local networks, and devices that are contained in the
  cache (are online)."
  [domain-string]
  (let [{:keys [exit out err]} (shell/sh "arp" domain-string)]
    (if (= 0 exit)
      (let [results (str/split-lines out)]
        (if (= 2 (count results))
          (get (str/split (second results) #"\s+") 2))))))

(defn mac-byte-to-string
  "Convert an array of bytes into the string representation of a mac
  address."
  [mac-bytes]
  (->> mac-bytes
       (map #(Integer/toHexString (bit-and % 0xff)))
       (interpose ":")
       (str/join)))

(defn mac-string-to-byte
  "Convert a string representation of a mac address into an array of
  bytes."
  [mac-string]
  (->> (str/split mac-string #"[\:-]")
       (map #(short (Integer/parseInt % 16)))))

(defn create-magic-packet
  "Returns a byte array with the first six bytes 0xff, and then the
  given mac address repeated 16 times."
  [mac-string]
  (let [mp (repeat 6 (short 0xff))
	mc (mac-string-to-byte mac-string)]
    (byte-array (reduce concat mp (repeat 16 mc)))))

(s/fdef wake-by-mac
  :args (s/alt :mac ::mac-string
               :mac-broadcast (s/cat ::mac-string ::ip-address))
  :ret #(instance? DatagramSocket %))

(defn wake-by-mac
  "Given a mac address as a string delinated via : or -, send a
  wake-on-lan broadcast packet to 255.255.255.255."
  ([mac-string]
   (wake-by-mac mac-string "255.255.255.255"))
  ([mac-string broadcast-address]
   (let [broadcast (InetAddress/getByName broadcast-address)
	 ^"[B" magic-packet-payload (create-magic-packet mac-string)
	 port 9
	 packet (DatagramPacket. magic-packet-payload
                                 (alength magic-packet-payload)
                                 broadcast
                                 port)]
     (doto (DatagramSocket.)
       (.send packet)
       (.close)))))

(defn wake-by-domain
  "(For debugging purposes only!) Send wake-on-lan broadcast packet for
  given domain name by querying the arp cache. This will of course
  invariably fail for devices currently offline, usually defeating the
  point of running this function."
  [domain-string]
  (if-let [mac-string (domain-to-mac domain-string)]
    (wake-by-mac mac-string)))
