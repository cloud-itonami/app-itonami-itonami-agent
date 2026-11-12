(ns itonami.otehon.job-contract
  "Operator-enrolled host capabilities. Profile text/model output is never a
  grant. No arbitrary URL, shell, mail send, registration POST or Git merge."
  (:require [itonami.otehon.bundle :as bundle]))
(def operations
  {"public-bots-registrar"
   [{:service "itonami-public-registry" :method "GET" :origin "https://itonami.cloud"
     :path "/api/marketplace/public-bots/metrics" :data-class "public"}
    {:service "itonami-public-registry" :method "GET" :origin "https://itonami.cloud"
     :path "/api/marketplace/public-bots/catalog" :data-class "public"}]
   "awai-arb"
   [{:service "itonami-public-registry" :method "GET" :origin "https://itonami.cloud"
     :path "/api/marketplace/public-bots/metrics" :data-class "public"}
    {:service "itonami-public-registry" :method "GET" :origin "https://itonami.cloud"
     :path "/api/marketplace/public-bots/catalog" :data-class "public"}]})
(def capabilities
  {"public-bots-registrar" ["registry_public_read" "result_write" "linear_record"]
   "awai-arb" ["registry_public_read" "result_write" "linear_record"]
   "rule-kaizen" ["repository_snapshot_scan" "encrypted_result_write" "linear_record"]})
(defn grant-cid [grant] (bundle/cid-raw (bundle/sorted-edn grant)))
(defn valid-grant? [g]
 (and (= "itonami.job-grant.v1" (:schema g)) (= "AWAI-2795" (:owner-decision g))
      (= "deterministic" (:execution g)) (contains? capabilities (:job-kind g))
      (= (:capabilities g) (get capabilities (:job-kind g)))
      (= (:operations g) (get operations (:job-kind g) []))
      (= "operator-enrollment" (:authority-source g))
      (false? (:model-tools g)) (false? (:mail-send g)) (false? (:git-merge g))))
(defn permitted? [grant operation]
 (and (valid-grant? grant) (some #{operation} (:operations grant))))
(defn public-registry-result [observations]
 (let [metrics (first observations) catalog (second observations)
       entries (get-in metrics [:json :tenants :registryEntries])
       items (or (get-in catalog [:json :bots]) (get-in catalog [:json :entries])
                 (when (vector? (:json catalog)) (:json catalog)))
       ids (when (vector? items) (keep #(if (string? %) % (:id %)) items))
       known? (and (= 200 (:http-status catalog)) (vector? items))]
  {:kind "public-bots-registrar" :metrics-http (:http-status metrics) :catalog-http (:http-status catalog)
   :registry-entries (when (and (= 200 (:http-status metrics)) (integer? entries) (<= 0 entries)) entries)
   :registration-state (if known? (if (some #{"awai-arb"} ids) "registered-live" "not-in-live-catalog") "unverified")
   :mission-completed (boolean (and known? (some #{"awai-arb"} ids)))
   :registration-posted false :email-sent false}))
(defn arb-proposals [observations slot]
 (let [facts (public-registry-result observations)
       gap? (and (= 200 (:metrics-http facts)) (integer? (:registry-entries facts))
                 (integer? (:catalog-http facts)) (not= 200 (:catalog-http facts)))]
  {:status "PENDING_POST" :provider "none" :model "deterministic"
   :proposals (if gap?
                [{:id (str "arb-" slot "-catalog-availability") :kind "information"
                  :substance-id (bundle/cid-raw "itonami.cloud/public-bots/catalog-availability")
                  :evidence (select-keys facts [:metrics-http :registry-entries :catalog-http])
                  :proposal "公開metricsとcatalogの可用性の差を調査し、catalogの配信経路を確認する。登録完了とは判定しない。"}]
                []) :posted false :invented-measurements false}))
