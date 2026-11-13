(ns itonami.otehon.mail-host-test
 (:require [clojure.test :refer [deftest is run-tests]] [cheshire.core :as json]
           [itonami.otehon.mail-host :as mail] [itonami.otehon.io :as io]
           [itonami.otehon.recurring-host :as d]))
(deftest credentials-never-reach-a-redirect-or-foreign-endpoint
 (let [root (.getCanonicalPath (java.io.File. (str ".agent-output/mail-host-" (random-uuid))))
       prior (System/getProperty "user.home") path (str root "/.itonami/profiles/mail-relay/state/relay-account.json")
       key "rk_fixture_not_a_real_secret_000000" a {:endpoint mail/endpoint :inbox "inbox_fixture"
                                               :inbox_key key :inbox_key_digest (io/sha256-utf8 key)}
       calls (atom 0) store! #(do (d/durable! path (json/generate-string %))
                                  (java.nio.file.Files/setPosixFilePermissions
                                    (java.nio.file.Paths/get path (make-array String 0))
                                    (java.nio.file.attribute.PosixFilePermissions/fromString "rw-------")))]
  (d/private-dir! (str root "/.itonami/profiles/mail-relay/state"))
  (try (System/setProperty "user.home" root)
   (with-redefs [mail/request! (fn [_] (swap! calls inc) {:http-status 200 :messages-count 0 :received true})]
    (is (thrown? Exception (mail/observe! path)))
    (is (zero? @calls))
    (store! (assoc a :endpoint "https://foreign.invalid"))
    (is (thrown? Exception (mail/observe! path)))
    (store! (assoc a :inbox "inbox_a/../../b"))
    (is (thrown? Exception (mail/observe! path)))
    (store! (assoc a :inbox_key_digest "wrong"))
    (is (thrown? Exception (mail/observe! path)))
    (is (zero? @calls))
    (store! a)
    (let [r (mail/observe! path)]
     (is (= 0 (:messages-count r))) (is (true? (:received r))) (is (false? (:sent r)))
     (is (not (clojure.string/includes? (pr-str r) key)))))
   (finally (System/setProperty "user.home" prior)))))
(let [r (run-tests)] (when (pos? (+ (:fail r) (:error r))) (System/exit 1)))
