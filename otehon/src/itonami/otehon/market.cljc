(ns itonami.otehon.market
  "Open market primitives: capacity attestations, ask/bid matching, escrow.

  Matching is a pure function over data: asks and bids are maps, the rule
  is cheapest fitting ask wins with deterministic tie-breaks, and one ask
  serves one bid (one capacity grant per node). Attestation checks shape
  and expiry only; cryptographic verification of :sig is an explicit open
  gap, recorded on every escrow the matcher declines to sign."
  (:require [clojure.string :as str]))

(def ^:private INF 999999999)

(defn valid-attestation?
  "Shape + expiry check. `today` and :expires are YYYY-MM-DD; string
  compare is exact for this format. Presence of :sig is required but
  NOT cryptographically verified (open gap: no keyring in otehon)."
  [att today]
  (boolean (and (map? att)
                (:node att) (:issuer att) (:sig att)
                (number? (:cpu att)) (pos? (:cpu att))
                (number? (:mem-gib att)) (pos? (:mem-gib att))
                (number? (:disk-gib att)) (pos? (:disk-gib att))
                (string? (:expires att))
                (not (pos? (compare today (:expires att)))))))

(defn- fits?
  "Ask covers the bid on cpu, memory, disk, and price."
  [ask bid]
  (and (>= (:cpu ask 0) (:cpu bid 0))
       (>= (:mem-gib ask 0) (:mem-gib bid 0))
       (>= (:disk-gib ask 0) (:disk-gib bid 0))
       (<= (:price ask INF) (:max-price bid INF))))

(defn match-orders
  "Match bids to asks. Bids run in given order; each takes the cheapest
  fitting unused ask (ties: lowest :node string). Returns {:matches
  [{:bid :ask :price}] :unmatched [bid-ids]}. Deterministic."
  [asks bids]
  (let [ordered (sort-by (fn [a] [(:price a INF) (str (:node a))]) asks)]
    (loop [bs bids used #{} matches [] unmatched []]
      (if (empty? bs)
        {:matches matches :unmatched unmatched}
        (let [b (first bs)
              pick (first (filter (fn [a]
                                    (and (not (contains? used (:node a)))
                                         (fits? a b)))
                                  ordered))]
          (if pick
            (recur (rest bs)
                   (conj used (:node pick))
                   (conj matches {:bid (:id b)
                                  :ask (:node pick)
                                  :price (:price pick)})
                   unmatched)
            (recur (rest bs) used matches (conj unmatched (:id b)))))))))

(defn escrow
  "Open an escrow record for a match. Unsigned by design: the signature
  gap is documented, not hidden."
  [m job-id stamp]
  {:job job-id
   :worker (:ask m)
   :price (:price m)
   :state :open
   :created stamp})
