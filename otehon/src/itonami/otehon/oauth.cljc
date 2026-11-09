(ns itonami.otehon.oauth
  "The portable sliver of itonami.agent.oauth: the token-cache file stem.
  Only `store-name` is ported here; discovery, client registration, token
  exchange, the loopback listener and the login/device flows stay a later
  slice (browser + network + secrets, S6)."
  (:require [clojure.string :as str]))

(defn store-name
  "The mcp-tokens file stem for one server: anything but [A-Za-z0-9_]
  becomes _, cut to 128 chars. The cut is a clamp: the legacy host
  (.cljk on Node) runs `subs` on JS substring semantics, which keeps a
  short string whole instead of throwing the way JVM Clojure does."
  [server]
  (let [s (str/replace (str server) #"[^A-Za-z0-9_]" "_")]
    (subs s 0 (min 128 (count s)))))
