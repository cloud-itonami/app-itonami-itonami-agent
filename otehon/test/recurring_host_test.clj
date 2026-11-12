(ns itonami.otehon.recurring-host-test
 (:require [clojure.test :refer [deftest is run-tests]] [clojure.edn :as edn]
           [itonami.otehon.recurring-host :as h]))
(def cfg {:orca "operator-cli" :workspace "kotobalabs"})
(def job {:run-id "run1" :binding {:issue-id "AWAI-1" :issue-uuid "issue1"}
          :enrollment {:project-id "project1"}})
(def issue {:ok true :result {:issue {:id "issue1" :project {:id "project1"} :state {:type "started"}}
 :comments [] :meta {:resolved {:workspaceId "kotobalabs"} :partial false :sections {:comments {:hasMore false}}}}})
(defn scratch [] (let [p (str ".agent-output/recurring-host-test-" (random-uuid))] (h/private-dir! p) p))
(deftest live-issue-binding-is-required
 (doseq [v [(assoc-in issue [:result :meta :resolved :workspaceId] "wrong")
            (assoc-in issue [:result :issue :state :type] "completed")
            (assoc-in issue [:result :issue :project :id] "wrong")]]
   (with-redefs [h/call-json! (fn [& _] v)]
     (is (thrown? Exception (h/issue-context! cfg job (scratch))))))
 (with-redefs [h/call-json! (fn [& _] issue)] (is (= issue (h/issue-context! cfg job (scratch))))))
(deftest comments-are-single-write-and-unknown-never-resends
 (let [dir (scratch) writes (atom 0)]
  (with-redefs [h/issue-context! (fn [& _] issue)
                h/call-json! (fn [argv _] (is (= "AWAI-1" (nth argv 4)))
                              (swap! writes inc) {:ok true :result {:comment {:id "comment1"}}})]
   (is (true? (h/publish-comment! cfg job dir "forecast" "body")))
   (is (true? (h/publish-comment! cfg job dir "forecast" "body")))
   (is (= 1 @writes))))
 (let [dir (scratch) writes (atom 0)]
  (with-redefs [h/issue-context! (fn [& _] issue)
                h/call-json! (fn [& _] (swap! writes inc) (throw (ex-info "lost ack" {})))]
   (is (thrown? Exception (h/publish-comment! cfg job dir "result" "body")))
   (is (false? (h/publish-comment! cfg job dir "result" "body")))
   (is (= 1 @writes)))
  (with-redefs [h/issue-context! (fn [& _] (assoc-in issue [:result :comments]
                                          [{:body "itonami-readiness:run1:result\n\nbody"}]))]
   (is (true? (h/publish-comment! cfg job dir "result" "body"))))))
(deftest partial-comment-reads-cannot-authorize-another-write
 (doseq [v [(assoc-in issue [:result :meta :partial] true)
            (assoc-in issue [:result :meta :sections :comments :hasMore] true)
            (update issue :result dissoc :comments)]]
  (with-redefs [h/issue-context! (fn [& _] v)
                h/call-json! (fn [& _] (throw (ex-info "must not write" {})))]
   (is (false? (h/publish-comment! cfg job (scratch) "result" "body"))))))
(deftest private-repositories-never-reach-inference-context
 (let [calls (atom 0)]
  (with-redefs [h/raw-json! (fn [& _] (swap! calls inc) {:private true :visibility "private"})]
   (is (thrown? Exception (h/public-context! (assoc cfg :github "gh")
                            {:public-repository "cloud-itonami/app-mail-relay"} (scratch))))
   (is (= 1 @calls))))
 (with-redefs [h/raw-json! (fn [& _] (throw (ex-info "must not call" {})))]
   (is (thrown? Exception (h/public-context! cfg {:public-repository "bad?route"} (scratch))))))
(let [x (run-tests)] (when (pos? (+ (:fail x) (:error x))) (System/exit 1)))
