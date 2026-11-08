(ns itonami.otehon.peer
  "Permissioned node sync: registry, sync plans, apply records.

  Nodes are known identities with a storage prefix, never anonymous
  endpoints. A sync plan diffs two manifests (pure data) and lists the
  digests to fetch or send; the host copies object files and re-hashes
  on receipt. Nothing is read, written, or moved here."
  (:require [itonami.otehon.cas :as cas]))

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

(defn apply-record
  "Describe an applied copy for the journal: digest, direction, bytes."
  [sha direction size verified?]
  {:sha sha :direction direction :size size :verified (boolean verified?)})

(defn repair-plan
  "Which bad rels a peer manifest can heal. `bad` is a verify-manifest
  result; `remote` the peer's manifest {rel {:sha :size}}. Returns
  sorted :repair [{:rel :sha :size}] (copy these objects from the peer
  and re-hash on receipt) and sorted :unrepairable [rel] (the peer
  lacks them, so the caller must refuse or escalate). Deterministic:
  rels are deduplicated and every list is sorted."
  [bad remote]
  (let [rels (sort (distinct (concat (:mismatch bad []) (:missing bad []))))]
    (reduce (fn [acc rel]
              (if-let [{:keys [sha size]} (get remote rel)]
                (update acc :repair conj {:rel rel :sha sha :size size})
                (update acc :unrepairable conj rel)))
            {:repair [] :unrepairable []}
            rels)))
