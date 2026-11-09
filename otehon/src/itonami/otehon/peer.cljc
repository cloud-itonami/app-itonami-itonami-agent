(ns itonami.otehon.peer
  "Permissioned node sync: registry, sync plans, apply records.

  Nodes are known identities with a storage prefix, never anonymous
  endpoints. A sync plan diffs two manifests (pure data) and lists the
  digests to fetch or send; the host copies object files and re-hashes
  on receipt. Nothing is read, written, or moved here.

  The messaging plane (ported from p2p.cljk) rides the same rule:
  crypto (keygen, sign, verify), the HTTP server, child processes and
  the wall clock stay host-side and enter through injected seams
  (`:sign`, `:verify`, `:fetch`, `:now-ms`, `:random-hex`, `:nonces`,
  `:exec-probe`). Envelope verdicts, trust checks, bundle and peer
  file writes, rail/path shaping and the service-record adapter are
  pure over those seams."
  (:require [clojure.string :as str]
            [itonami.otehon.bundle :as bundle]
            [itonami.otehon.cas :as cas]
            [itonami.otehon.ed25519 :as ed25519]
            [itonami.otehon.io :as hio]))

(defn register-node
  "Admit `node` with the storage `prefix` it may write."
  [registry node prefix]
  (assoc registry node {:prefix prefix}))

(defn grant-until
  "Bound a node's grant to `until` (YYYY-MM-DD, inclusive)."
  [registry node until]
  (assoc-in registry [node :until] until))

(defn node-prefix
  "The storage prefix `node` may write, nil when unknown."
  [registry node]
  (get-in registry [node :prefix]))

(defn member?
  "A registered node whose grant has not expired. `today` is YYYY-MM-DD;
  string compare is exact for this format. A nil :until never expires."
  [registry node today]
  (let [e (get registry node)]
    (boolean (and e
                  (:prefix e)
                  (or (nil? (:until e))
                      (not (pos? (compare today (:until e)))))))))

(defn sync-plan
  "Diff two manifests ({rel {:sha :size}}). Returns sorted :fetch (digests
  the remote side has and we lack, with sizes) and :send, plus the
  :same count. Deterministic: every list is sorted by digest."
  [local remote]
  (let [lm (into {} (map (fn [[_ {:keys [sha size]}]] [sha size]) local))
        rm (into {} (map (fn [[_ {:keys [sha size]}]] [sha size]) remote))
        lks (set (keys lm))
        rks (set (keys rm))
        fetch (sort (remove lks rks))
        send (sort (remove rks lks))]
    {:fetch (mapv (fn [s] {:sha s :size (get rm s)}) fetch)
     :send (mapv (fn [s] {:sha s :size (get lm s)}) send)
     :same (count (filter lks rks))}))

(defn fleet-copies
  "Union view of a fleet: `remotes` [{:peer :manifest}] in any order.
  Returns {:copies {sha {:peers [sorted] :count n}} :peers [sorted]
  :objects n}. Read-only planning input for replication and repair
  triage. Deterministic: everything sorted."
  [remotes]
  (let [by-sha (reduce (fn [acc {:keys [peer manifest]}]
                         (reduce (fn [a [_ {:keys [sha]}]]
                                   (update a sha (fnil conj #{}) peer))
                                 acc manifest))
                       {} remotes)
        copies (into {} (map (fn [[sha peers]]
                               [sha {:peers (vec (sort peers)) :count (count peers)}])
                             by-sha))]
    {:copies copies
     :peers (vec (sort (distinct (map :peer remotes))))
     :objects (count copies)}))

(defn replication-plan
  "Which digests fall below `desired` copies. `copies` is the :copies
  map from fleet-copies. Returns {:under [{:sha :have :want}] (sorted,
  have < desired) :healthy n}. Deterministic."
  [copies desired]
  (let [under (vec (sort-by :sha
                            (keep (fn [[sha {:keys [count]}]]
                                    (when (< count desired)
                                      {:sha sha :have count :want desired}))
                                  copies)))]
    {:under under :healthy (- (count copies) (count under))}))

(defn apply-record
  "Describe an applied copy for the journal: digest, direction, bytes."
  [sha direction size verified?]
  {:sha sha :direction direction :size size :verified (boolean verified?)})

(defn repair-plan
  "Which bad rels a list of peers can heal. `bad` is a verify-manifest
  result; `remotes` is [{:peer :manifest}] in try-order, one entry per
  admitted peer dir. Returns sorted :repair [{:rel :sha :size :from}]
  (copy each object from :from and re-hash on receipt) and sorted
  :unrepairable [rel] (no peer lists them, so the caller must refuse
  or escalate). The first peer listing a rel wins. Deterministic:
  rels are deduplicated and every list is sorted."
  [bad remotes]
  (let [rels (sort (distinct (concat (:mismatch bad []) (:missing bad []))))]
    (reduce (fn [acc rel]
              (if-let [hit (some (fn [{:keys [peer manifest]}]
                                   (when-let [{:keys [sha size]} (get manifest rel)]
                                     {:sha sha :size size :from peer}))
                                 remotes)]
                (update acc :repair conj (assoc hit :rel rel))
                (update acc :unrepairable conj rel)))
            {:repair [] :unrepairable []}
            rels)))

;; ---------------------------------------------------------------- messaging plane (p2p.cljk)

(def protocol "itonami-agent-p2p/1")

;; ---------------------------------------------------------------- did:key

(def ^:private b58
  "Bitcoin base58 alphabet, verbatim from p2p.cljk."
  "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz")

(defn- b58-index [c]
  (str/index-of b58 (str c)))

(defn unbase58btc
  "Base58btc text -> byte vector. The digit-array decode (no bigint
  interop), so every runner walks the same bytes."
  [s]
  (let [chars (vec (str s))
        zeros (count (take-while #(= "1" (str %)) chars))]
    (loop [rest-chars (drop zeros chars) out []]
      (if (empty? rest-chars)
        (vec (concat (repeat zeros 0) out))
        (let [carry (b58-index (first rest-chars))]
          (when (nil? carry)
            (throw (ex-info (str "bad base58 char: " (first rest-chars)) {})))
          (let [[new-out new-carry]
                (loop [i (dec (count out)) o out c carry]
                  (if (neg? i)
                    [o c]
                    (let [x (+ (* (nth o i) 58) c)]
                      (recur (dec i) (assoc o i (mod x 256)) (quot x 256)))))]
            (recur (rest rest-chars)
                   (if (pos? new-carry)
                     (vec (concat (loop [c new-carry acc []]
                                    (if (pos? c)
                                      (recur (quot c 256) (conj acc (mod c 256)))
                                      (reverse acc)))
                                  new-out))
                     new-out))))))))
(defn parse-did
  "The 32 raw key bytes for a did:key ed25519 id, or nil when the
  text is not one (bad prefix, bad alphabet, wrong length)."
  [did]
  (when-let [[_ z] (re-matches #"^did:key:z(.+)$" (str did))]
    (let [b (try (unbase58btc z) (catch #?(:lpy python/Exception :clj Exception :cljs :default) _ nil))]
      (when (and (= 34 (count b)) (= 237 (nth b 0)) (= 1 (nth b 1)))
        (vec (subvec (vec b) 2))))))

(defn valid-did?
  "True when `did` parses as a did:key ed25519 id."
  [did]
  (boolean (parse-did did)))

(defn pub->did
  "The did:key id for 32 raw public-key bytes (a seq of numbers)."
  [raw]
  (str "did:key:z" (ed25519/base58btc (vec (concat [237 1] raw)))))

;; ---------------------------------------------------------------- envelopes

(defn sign-envelope
  "The signed envelope map for `payload-map`: the payload carries ts +
  nonce + protocol, `:sign` [payload-str] answers the base64 signature
  (host crypto). Time and randomness ride `opts` so cases pin them."
  ([node payload-map] (sign-envelope node payload-map {}))
  ([node payload-map {:keys [now-ms random-hex sign]}]
   (let [payload (hio/json-str (assoc payload-map
                                      "ts" ((or now-ms hio/now-ms))
                                      "nonce" ((or random-hex hio/random-hex) 12)
                                      "protocol" protocol))]
     {"payload" payload
      "did" (:did node)
      "sig" (sign payload)})))

(defn open-envelope
  "Verify an envelope. `:verify` [payload sig did] answers truthy for
  a good signature (host crypto); `:now-ms` and the `:nonces` atom
  (nonce -> seen-at ms) drive replay/staleness. Returns {:did
  :payload} or {:error reason}, verbatim reasons from the legacy."
  [{:strs [payload did sig]} {:keys [trusted verify now-ms nonces]}]
  (cond
    (not (valid-did? (str did))) {:error "bad did"}
    (not (try (boolean (verify (str payload) (str sig) (str did)))
              (catch #?(:lpy python/Exception :clj Exception :cljs :default) _ false)))
    {:error "bad signature"}
    :else
    (let [pl (hio/parse-json (str payload))
          age (let [d (- ((or now-ms hio/now-ms)) (get pl "ts" 0))] (if (neg? d) (- d) d))]
      (cond
        (> age 120000) {:error "stale envelope"}
        (contains? @nonces (get pl "nonce")) {:error "replayed nonce"}
        (and trusted (not (contains? trusted did))) {:error (str "untrusted peer " did)}
        :else
        (do
          (swap! nonces
                 (fn [m]
                   (let [nw ((or now-ms hio/now-ms))]
                     (into {} (filter (fn [e] (> (+ (val e) 300000) nw))
                                      (assoc m (get pl "nonce") nw))))))
          {:did did :payload pl})))))

(defn- url-encode [s]
  #?(:lpy (let [u (python/__import__ "urllib.parse" ** :fromlist ["quote"])] (.quote u (str s) ** :safe ""))
     :clj (str/replace (java.net.URLEncoder/encode (str s) "UTF-8") "+" "%20")
     :cljs (js/encodeURIComponent (str s))))

(defn auth-header
  "The X-Itonami-Envelope header for a request envelope (signing rides
  `opts`, as in sign-envelope)."
  [node payload-map opts]
  {"X-Itonami-Envelope" (url-encode (hio/json-str (sign-envelope node payload-map opts)))})

;; ---------------------------------------------------------------- peers.edn

(defn peers-file
  "The peers file under `dir` (the node home in production)."
  [dir]
  (str dir "/peers.edn"))

(defn read-peers
  "The trust map under `dir`, defaulting to empty trust."
  [dir]
  (let [f (peers-file dir)]
    (merge {:trusted #{} :peers []}
           (when (hio/file? f) (hio/parse-edn (hio/slurp-utf8 f))))))

(defn write-peers!
  "Persist the trust map (sorted EDN with the legacy header comment).
  Answers the normalized map."
  [dir m]
  (let [norm (-> m (update :trusted set) (update :peers #(vec (distinct %))))
        text (str ";; itonami-agent peers. These may fetch bundles and run turns here.\n"
                  ";; :trusted is a set of DIDs. :peers is a vector of {:url ... :did ...} entries.\n"
                  (bundle/sorted-edn norm) "\n")]
    (hio/mkdirs! (hio/dirname (peers-file dir)))
    (hio/write-text! (peers-file dir) text)
    norm))

(defn add-peer!
  "Record a peer (and trust its did when asked). Answers the
  normalized trust map."
  [dir {:keys [url did trust?]}]
  (let [m (read-peers dir)]
    (write-peers! dir (cond-> m
                        url (update :peers conj (cond-> {:url url} did (assoc :did did)))
                        (and did trust?) (update :trusted conj did)))))

;; ---------------------------------------------------------------- rails and paths

(defn ensure-rail
  "A turn run for a peer appends the requester's :inference-rail to
  the profile config when the profile does not already name it:
  murakumo (the operator rail) or itonami-p2p (this node's own rails,
  then its trusted peers'). Verbatim from p2p.cljk."
  [config rail]
  (let [has? (fn [c r] (or (= r (get-in c ["model" "provider"]))
                           (some #(= r (get % "provider")) (get c "fallback_providers"))))
        add (fn [c r m] (if (has? c r) c (update c "fallback_providers" (fnil conj []) {"provider" r "model" m})))]
    (cond-> config
      rail (add rail (if (= rail "murakumo") "murakumo/free" "auto")))))

(defn rebase-paths
  "Rewrite requester-home paths into the run's own copy:
  <origin-home>/workspace -> the run workspace (whose files travel
  back), <origin-home> -> the materialized bundle home, plus the
  ~/.itonami and ~/.hermes profile-home spellings. Verbatim."
  [text {:keys [origin-home profile ws home]}]
  (if (or (nil? text) (nil? origin-home))
    text
    (let [tildes [(str "~/.itonami/profiles/" profile) (str "~/.hermes/profiles/" profile)]]
      (reduce (fn [t [from to]] (str/replace t from to))
              (str text)
              (concat [[(str origin-home "/workspace") ws] [origin-home home]]
                      (for [td tildes] [(str td "/workspace") ws])
                      (for [td tildes] [td home]))))))

;; ---------------------------------------------------------------- bundles

(defn materialize-bundle!
  "Write files under <dir>/peer-runs/<cid>/home and answer that home.
  A present .complete marker means a previous materialization won;
  `..` and absolute rels are refused, verbatim from the legacy."
  [dir cid files]
  (let [home (str dir "/peer-runs/" cid "/home")]
    (when-not (hio/file? (str home "/.complete"))
      (hio/remove-dir! home)
      (doseq [[rel content] files]
        (when (or (str/includes? (str rel) "..") (str/starts-with? (str rel) "/"))
          (throw (ex-info (str "unsafe path in bundle: " rel) {})))
        (let [p (str home "/" rel)]
          (hio/mkdirs! (hio/dirname p))
          (hio/write-text! p (str content))))
      (hio/write-text! (str home "/.complete") (str cid)))
    home))

(defn fetch-bundle
  "Pull a bundle by CID from origin-url and verify it. `:fetch`
  answers response maps; `:sign` builds the request envelope (both
  injected). Resolves to the files map; throws verbatim legacy
  errors."
  [node origin-url cid {:keys [fetch sign]}]
  (let [r (fetch (str origin-url "/itonami/v1/bundle/" cid)
                 {:method "GET" :headers (auth-header node {"get" cid} {:sign sign}) :body nil})]
    (cond
      (not= 200 (:status r))
      (throw (ex-info (str "bundle fetch " (:status r) ": " (subs (str (:text r)) 0 (min 200 (count (str (:text r)))))) {}))
      (not (= cid (bundle/bundle-cid (get-in r [:body "files"]))))
      (throw (ex-info (str "bundle " cid " failed hash verification") {}))
      :else (get-in r [:body "files"]))))

;; ---------------------------------------------------------------- service records (ADR-0145)

(def service-record-keys
  "The netmap :service projection this adapter consumes (dependency-free
  local copy of kekkai.service-record's shape contract: required keys,
  non-empty digest and model id, positive epoch)."
  [:service/did :service/multiaddr :service/engine :service/model-id
   :service/digest :service/epoch])

(defn- service-record? [rec]
  (and (map? rec)
       (every? #(contains? rec %) service-record-keys)
       (string? (:service/did rec)) (seq (:service/did rec))
       (string? (:service/model-id rec)) (seq (:service/model-id rec))
       (string? (:service/digest rec)) (seq (:service/digest rec))
       (integer? (:service/epoch rec)) (pos? (:service/epoch rec))))

(defn service-records->peer-infos
  "kekkai :netmap/services entries -> peer-info fragments (`:did` for
  the trust check, `:models` for the lend check, `:load` for the
  least-loaded sort). Invalid records are dropped, not repaired;
  same-did records merge their model lists. Deterministic."
  [services]
  (->> services
       (filter service-record?)
       (group-by :service/did)
       (sort-by key)
       (mapv (fn [[did recs]]
               {:did did
                :models (->> recs (map :service/model-id) distinct sort vec)
                :load 0}))))

(defn overlay-service-records
  "Merge kekkai service records into discovered peer infos: a record is
  the offer of record for its did (live discovery says who is up and
  how loaded, records say what they offer). Dids with no records keep
  their infos; unreachable dids are never added. Deterministic."
  [infos services]
  (let [by-did (group-by :service/did (filter service-record? services))]
    (mapv (fn [i]
            (if-let [recs (get by-did (:did i))]
              (assoc i :models (->> recs (map :service/model-id) distinct sort vec))
              i))
          (vec infos))))

;; ---------------------------------------------------------------- http mapping

(defn http-json
  "The fetch response mapping: {:status :body (parsed or nil) :text}.
  `:fetch` [url opts] answers {:status n :body-text s} (or throws,
  which callers map). `:timeout-s` rides the call shape for parity
  with the legacy but no timers run in .cljc."
  [fetch url {:keys [method body headers timeout-s]}]
  (let [res (fetch (str url) {:method (or method "GET")
                              :headers (merge {"Content-Type" "application/json"} headers)
                              :body body :timeout-s timeout-s})]
    {:status (:status res)
     :text (str (:body-text res))
     :body (try (hio/parse-json (str (:body-text res)))
                (catch #?(:lpy python/Exception :clj Exception :cljs :default) _ nil))}))
