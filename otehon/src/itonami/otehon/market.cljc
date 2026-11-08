(ns itonami.otehon.market
  "Open market primitives: capacity attestations, ask/bid matching, escrow.

  Matching is a pure function over data: asks and bids are maps, the rule
  is cheapest fitting ask wins with deterministic tie-breaks, and one ask
  serves one bid (one capacity grant per node). Attestations carry an
  HMAC-SHA256 :sig over a canonical payload, verified against a
  per-issuer shared secret (permissioned issuers; the secret leaves the
  keyring only at the CLI boundary). Asymmetric signatures stay open:
  there is no portable ed25519 in .cljc, so open-market strangers still
  cannot be verified — only admitted issuers."
  (:require [clojure.string :as str]
            [itonami.otehon.cas :as cas]
            [itonami.otehon.text :as text]))

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

(def ^:private latin1-table
  "All 256 latin-1 chars in code order, built with `char` (portable on
  all three runners, as text.cljc already proves). Index of a char is
  its code point — the portable inverse of `char`."
  (apply str (map char (range 256))))

(defn- encode-latin1
  "String to latin-1 byte vector, nil when a char falls outside 0-255.
  Fail-closed: unencodable text never gets signed or verified."
  [s]
  (let [s (str s) n (count s)]
    (loop [i 0 out []]
      (if (= i n)
        out
        (let [code (text/index-of latin1-table (subs s i (inc i)))]
          (if (nil? code) nil (recur (inc i) (conj out code))))))))

(defn- hmac-sha256
  "RFC 2104 HMAC-SHA256 over byte vectors, pure. Keys longer than the
  64-byte block are hashed first, shorter ones zero-padded."
  [key-bytes msg-bytes]
  (let [k (vec (if (> (count key-bytes) 64)
                 (cas/sha256-digest key-bytes)
                 key-bytes))
        padded (into k (repeat (- 64 (count k)) 0))
        regions (fn [padc] (mapv #(bit-xor % padc) padded))]
    (cas/sha256-digest (concat (regions 0x5c)
                               (cas/sha256-digest (concat (regions 0x36) msg-bytes))))))

(def ^:private sig-fields [:node :cpu :mem-gib :disk-gib :price :expires :issuer])

(defn- payload-bytes
  "Canonical signing payload: sig-fields joined with `|`, latin-1.
  nil when a field is missing or unencodable — never sign ambiguity."
  [att]
  (when (every? #(contains? att %) sig-fields)
    (encode-latin1 (str/join "|" (map #(str (get att %)) sig-fields)))))

(defn sign-attestation
  "Mint :sig for `att` with the issuer's shared `secret` (a string).
  Returns the attestation with :sig, or nil when the payload is
  missing a field or leaves latin-1. The secret travels the CLI
  boundary only; it never lands in a manifest, ask file, or case."
  [att secret]
  (when-let [pb (payload-bytes att)]
    (when-let [kb (encode-latin1 secret)]
      (assoc att :sig (cas/bytes-hex (hmac-sha256 kb pb))))))

(defn valid-signature?
  "True when `att` carries the :sig its :issuer would mint with the
  secret registered in `secrets` {issuer secret}. False for unknown
  issuers, missing secrets, and any payload or signature mismatch.
  Expiry is NOT checked here; compose with valid-attestation?."
  [att secrets]
  (boolean (and (map? att) (map? secrets)
                (:sig att) (:issuer att)
                (let [secret (get secrets (:issuer att))]
                  (and (string? secret)
                       (= (:sig att) (:sig (sign-attestation att secret))))))))

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

(defn match-signed
  "match-orders with the signature gate. `secrets` {issuer secret}:
  every ask must carry the :sig its issuer would mint, else the whole
  match is refused with :reason market/signature-invalid and sorted
  :nodes. nil `secrets` skips the gate (legacy presence-only path,
  kept for unsigned callers and documented at the CLI)."
  [asks bids secrets]
  (if (nil? secrets)
    (match-orders asks bids)
    (let [bad (sort (map :node (remove #(valid-signature? % secrets) asks)))]
      (if (seq bad)
        {:status :refused :reason "market/signature-invalid" :nodes (vec bad)}
        (match-orders asks bids)))))

(defn escrow
  "Open an escrow record for a match. Still unsigned by design (escrow
  signing stays open); the capacity attestation behind the match is
  what carries the verified :sig."
  [m job-id stamp]
  {:job job-id
   :worker (:ask m)
   :price (:price m)
   :state :open
   :created stamp})
