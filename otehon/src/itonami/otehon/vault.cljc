(ns itonami.otehon.vault
  "The credential-vault pure store core. Faithful port of the pure
  projections of itonami.agent.vault (the local encrypted login store
  behind `itonami-agent vault add/list/remove` and the browser_vault
  tools): login records are plain maps under string keys (\"label\",
  \"username\", \"password\", \"origin\", \"totp_seed\") keyed by handle,
  and every unit here takes that store map explicitly.

  Host-side and staying S6: the encrypted file (vault.json.enc +
  the 0600 key file, AES-256-GCM via node:crypto), handle generation
  (`vh-` + random hex), the stdin password read and the CLI itself.
  TOTP minting (RFC 6238 over the stored seed) is a later slice."
  (:require [clojure.string :as str]))

(defn login-meta
  "What the agent may see for one handle: labels and metadata, never
  passwords or seeds. nil when the handle is unknown."
  [store handle]
  (when-let [r (get store (str handle))]
    {"handle" (str handle) "backend" "local" "label" (get r "label" "")
     "kind" "login" "origin" (get r "origin" "")
     "available" (not (str/blank? (str (get r "origin" ""))))
     "identifier" (get r "username" "") "identifier_type" "username"
     "two_factor" (if (not (str/blank? (str (get r "totp_seed" "")))) "automatic" nil)}))

(defn add-login
  "Store one login record under `handle`. Returns the updated store;
  the handle is generated host-side (the CLI), like the legacy."
  [store {:keys [label username password origin totp-seed]} handle]
  (assoc store (str handle) {"label" (str label) "username" (str username)
                             "password" (str password) "origin" (str origin)
                             "totp_seed" (str (or totp-seed ""))}))

(defn remove-login
  "Drop one handle. Returns {:store :removed}; removing an unknown
  handle keeps the store and reports false, as the CLI's
  \"no such handle\" line does."
  [store handle]
  (let [h (str handle)]
    (if (contains? store h)
      {:store (dissoc store h) :removed true}
      {:store store :removed false})))

(defn secret
  "The password or TOTP seed for a handle. Internal plumbing only —
  never returned to the model."
  [store handle field]
  (get (get store (str handle)) field))

(defn- pad-right
  "Pad `s` with spaces to `width`, never truncating (mirrors the
  legacy list column widths)."
  [s width]
  (let [s (str s) n (count s)]
    (if (< n width) (str s (apply str (repeat (- width n) " "))) s)))

(defn format-row
  "One `vault list` output line for a [handle record] entry."
  [[handle r]]
  (str (pad-right handle 14) (pad-right (str (get r "username")) 20)
       (str (get r "origin")) "  " (str (get r "label"))))

(defn list-rows
  "The whole store as sorted [handle record] vectors, the order
  `vault list` prints (normalized to vectors so every runner compares
  the same shape). Empty store prints \"no saved logins\" (S6 CLI)."
  [store]
  (sort-by first (mapv (fn [e] [(key e) (val e)]) store)))
