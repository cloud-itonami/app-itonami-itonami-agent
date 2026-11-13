(ns itonami.otehon.etcd-test
 (:require [clojure.test :refer [deftest is run-tests]] [clojure.string :as s] [itonami.otehon.etcd :as e]))
(def k (str "/itonami/occurrence/v1/run/" (apply str (repeat 64 "a"))))
(deftest transaction-data-cannot-inject-commands
 (let [payload "hostile\n\nput /outside stolen\n\n\"quoted\""
       input (e/transaction-input {:compare {k nil} :put {k payload}})
       lines (s/split-lines input)]
  (is (= 1 (count (filter #(s/starts-with? % "put ") lines))))
  (is (s/includes? input "\\n\\nput /outside"))
  (is (not (s/includes? input "\nput /outside")))))
(deftest namespace-and-size-fail-before-network
 (is (thrown? Exception (e/transaction-input {:compare {"/other" nil} :put {k "v"}})))
 (is (thrown? Exception (e/transaction-input {:compare {k nil} :put {k (apply str (repeat 65537 "a"))}})))
 (is (thrown? Exception (e/transaction-input {:compare {} :put {k "v"}}))))
(deftest certificate-formatting-keeps-the-exact-node-identity
 (is (= "jun-control" (e/certificate-cn "subject=CN=jun-control\n")))
 (is (= "jun-control" (e/certificate-cn "subject= CN=jun-control\n")))
 (is (not= "jun-control" (e/certificate-cn "subject= CN=other-node\n")))
 (is (nil? (e/certificate-cn "subject=O=no-node\n"))))
(deftest occurrence-and-daily-budget-are-one-bounded-cas
 (let [ks (mapv #(str "/itonami/occurrence/v1/cursor/" (apply str (repeat 64 %))) ["a" "b" "c" "d" "e"])
       tx {:compare (zipmap (take 4 ks) (repeat nil)) :put (zipmap (take 3 ks) (repeat "v"))}]
  (is (string? (e/transaction-input tx)))
  (is (thrown? Exception (e/transaction-input (assoc-in tx [:compare (last ks)] nil))))
  (is (thrown? Exception (e/transaction-input (assoc-in tx [:put (nth ks 3)] "v"))))))
(let [r (run-tests)] (when (pos? (+ (:fail r) (:error r))) (System/exit 1)))
