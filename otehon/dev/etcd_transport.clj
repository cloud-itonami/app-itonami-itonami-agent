(ns etcd-transport
  "EXPERIMENTAL developer transport, not an approved Otehon production backend.
  Existing authenticated HTTPS endpoints only; no server provisioning or fallback."
  (:require [cheshire.core :as json] [clojure.edn :as edn]
            [clojure.java.shell :as sh] [clojure.string :as str]))
(defn- b64 [s] (.encodeToString (java.util.Base64/getEncoder) (.getBytes s "UTF-8")))
(defn- unb64 [s] (String. (.decode (java.util.Base64/getDecoder) ^String s) "UTF-8"))
(defn- scope! [key] (when-not (and (string? key) (re-matches #"/itonami/occurrence/v1/(enrollment|cursor|run)/[a-f0-9]{64}" key))
                        (throw (ex-info "out-of-scope key" {}))))
(defn client [{:keys [endpoints ca cert key cluster-id authenticated-node]}]
  (when-not (and (vector? endpoints) (<= 3 (count endpoints) 7)
                 (= (count endpoints) (count (distinct endpoints)))
                 (every? #(re-matches #"https://[A-Za-z0-9.:-]+" %) endpoints)
                 (string? cluster-id) (re-matches #"[0-9]+" cluster-id)
                 (string? authenticated-node) (re-matches #"[A-Za-z0-9-]{1,64}" authenticated-node)
                 (every? #(.isFile (java.io.File. %)) [ca cert key]))
    (throw (ex-info "pinned authenticated quorum endpoints required" {})))
  (let [subject (sh/sh "openssl" "x509" "-in" cert "-noout" "-subject" "-nameopt" "RFC2253")]
    (when-not (and (zero? (:exit subject))
                  (= authenticated-node (second (re-find #"(?:^|[,=])CN=([^,\n]+)" (str/replace (:out subject) "subject=" "")))))
      (throw (ex-info "client certificate identity mismatch" {}))))
  (let [active (atom nil)
        request (fn [endpoint route body]
                  (let [r (sh/sh "curl" "--fail" "--silent" "--show-error" "--max-time" "4"
                                 "--max-filesize" "1048576" "--proto" "=https" "--max-redirs" "0"
                                 "--cacert" ca "--cert" cert "--key" key
                                 "-H" "Content-Type: application/json" "-X" "POST" "--data-binary" "@-"
                                 (str endpoint route) :in (json/generate-string body))]
                    (when-not (zero? (:exit r)) (throw (ex-info "authenticated transport unavailable" {})))
                    (let [v (json/parse-string (:out r) true)]
                      (when-not (= cluster-id (get-in v [:header :cluster_id])) (throw (ex-info "cluster mismatch" {}))) v)))
        read-one (fn [key]
                   (scope! key)
                   (loop [[endpoint & more] endpoints]
                     (when-not endpoint (throw (ex-info "no linearizable endpoint" {})))
                     (let [attempt (try {:ok (request endpoint "/v3/kv/range" {:key (b64 key) :serializable false})}
                                        (catch Exception _ {:failed true}))]
                       (if (:failed attempt) (recur more)
                         (do (reset! active endpoint)
                             (when-let [value (get-in attempt [:ok :kvs 0 :value])]
                               (edn/read-string (unb64 value))))))))
        txn (fn [{:keys [compare put]}]
              (when-not (and (map? compare) (map? put) (<= 1 (count compare) 3) (<= 1 (count put) 2))
                (throw (ex-info "bounded CAS transaction required" {})))
              (doseq [k (concat (keys compare) (keys put))] (scope! k))
              (when-not @active (throw (ex-info "linearizable read required before write" {})))
              (let [body {:compare (mapv (fn [[k v]] (if (nil? v)
                                                      {:key (b64 k) :target "VERSION" :result "EQUAL" :version "0"}
                                                      {:key (b64 k) :target "VALUE" :result "EQUAL" :value (b64 v)})) compare)
                          :success (mapv (fn [[k v]] {:requestPut {:key (b64 k) :value (b64 v)}}) put)
                          :failure []}
                    r (request @active "/v3/kv/txn" body)]
                {:succeeded (true? (:succeeded r)) :revision (get-in r [:header :revision])}))]
    {:read read-one :transact txn :authenticated-node authenticated-node}))
