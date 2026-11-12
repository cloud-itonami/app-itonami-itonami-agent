(ns itonami.otehon.muse-test
 (:require [clojure.test :refer [deftest is run-tests]]
           [itonami.otehon.muse :as m] [itonami.otehon.muse-host :as host]))
(def p {:status :ok :model "muse-spark-1.3" :reasoning-effort "low"})
(defn event [kind seq payload]
 {:payload_type kind :sequence seq :stream {:kind "session" :id "s"}
  :payload (merge {:run_stream {:id "r"}} payload)})
(def configured (event "run.model.configured" 1 {:provider_id "meta" :model_id "muse-spark-1.3"}))
(def terminal (event "run.terminal.completed" 3 {:terminal "completed" :text "READY"}))
(def usage (event "run.usage" 2 {:models [{:input_tokens 12 :output_tokens 3 :reasoning_tokens 2}]}))
(deftest complete-requires-real-config-and-terminal
 (is (= "completed" (:state (m/result p [configured usage terminal] 0))))
 (is (= "unknown" (:state (m/result p [terminal] 0))))
 (is (= "unknown" (:state (m/result p [configured terminal] 3))))
 (is (= "unknown" (:state (m/result p [(assoc-in configured [:payload :provider_id] "echo") terminal] 0))))
 (is (= "unknown" (:state (m/result p [configured (assoc-in terminal [:payload :run_stream :id] "other")] 0)))))
(deftest cumulative-usage-is-not-summed
 (is (= 12 (get-in (m/result p [configured usage usage terminal] 0) [:usage :models 0 :input_tokens])))
 (is (= "unknown" (:state (m/result p [configured usage (assoc-in usage [:payload :models 0 :input_tokens] 15) terminal] 0)))))
(deftest tool-calls-and-unpinned-binding-refused
 (is (= "unknown" (:state (m/result p [configured (event "tool.call.started" 2 {}) terminal] 0))))
 (is (not (host/binding-valid? {}))))
(deftest profile-gates-before-any-provider-call
 (let [base (host/load-profile "otehon/test/fixtures/itonami-ok")
       cap {:capability :inference_muse :decision :autonomous}
       allowed (-> base
                   (assoc-in [:config "model"] {"provider" "muse" "base_url" "https://api.meta.ai/v1" "default" "muse-spark-1.3"})
                   (update :config dissoc "providers")
                   (assoc :jobs [])
                   (assoc-in [:manifest :placement :inference-rail] :muse)
                   (assoc-in [:manifest :inference :rails] [:muse])
                   (assoc-in [:manifest :inference :authorities] ["api.meta.ai"])
                   (assoc-in [:manifest :capabilities] [cap])
                   (assoc-in [:yakuwari :yakuwari/capabilities] [cap]))]
   (is (= :ok (:status (m/plan allowed "Public probe"))))
   (is (not= :ok (:status (m/plan base "Public probe"))))
   (is (not= :ok (:status (m/plan (assoc-in allowed [:yakuwari :yakuwari/capabilities] []) "Public probe"))))
   (is (not= :ok (:status (m/plan (assoc-in allowed [:config "model" "base_url"] "https://other.example") "Public probe"))))))
(let [r (run-tests)] (when (pos? (+ (:fail r) (:error r))) (System/exit 1)))
