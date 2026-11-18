(ns itonami.app.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [itonami.app.core :as core]
            #?(:cljs [cljs.reader :as reader] :clj [clojure.edn :as edn])
            #?(:clj [cheshire.core :as json])))

(def readers
  {:parse-edn #?(:cljs reader/read-string :clj edn/read-string)
   :parse-json #?(:cljs (fn [s] (js->clj (.parse js/JSON s))) :clj json/parse-string)})

(def manifest
  "{:schema \"cloud.itonami.profile.v2\" :name \"t\" :cron? true
    :inference {:rails [:murakumo] :authorities [\"murakumo.awai.network\"] :deny-hosts [\"openrouter.ai\"]}}")

(def jobs
  "{\"jobs\":[{\"id\":\"j1\",\"name\":\"daily\",\"schedule\":{\"kind\":\"cron\",\"expr\":\"0 9 * * *\"},\"enabled\":true,\"prompt\":\"hi\"}]}")

(deftest hermes-profile-is-not-itonami
  (let [r (core/report "h" {"SOUL.md" "x" "config.yaml" "model: gpt\n"} readers)]
    (is (= "hermes" (:runtime r)))
    (is (= [] (:jobs r)))))

(deftest broken-edn-is-reported-not-thrown
  (let [r (core/report "b" {"SOUL.md" "x" "itonami.edn" "{:unclosed"} readers)]
    (is (= "itonami" (:runtime r)))
    (is (false? (:usable? r)))
    (is (some #(= "itonami.edn" (:file %)) (:problems r)))))

(deftest jobs-get-a-dry-run-row
  (let [r (core/report "t" {"SOUL.md" "x" "itonami.edn" manifest "cron/jobs.json" jobs} readers)]
    (is (= 1 (count (:jobs r))))
    (is (= "j1" (:id (first (:jobs r)))))
    (is (keyword? (:status (first (:jobs r)))))))

(deftest reports-are-sorted
  (is (= ["a" "b"] (map :profile (core/reports {"b" {} "a" {}} readers)))))
