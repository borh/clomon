(ns clomon.reporting
  (:require
   [hiccup.page :refer [html5]]
   [ring.util.codec :as codec]
   [oz.core :as oz]
   [clomon.db :refer [cached-reports outages monitor-report]]
   [tick.alpha.api :as t]
   [clomon.db :as db]
   [clomon.config :refer [config]]))

;; TODO:  etc...
;; [:span.icon.has-text-warning ]

(def prefix (-> config :clomon/server :prefix))

(defn pretty-render [m]
  (cond-> m
          (:target m) (update :target (fn [target] [:a {:href (str prefix "/target/" (codec/url-encode target) "/" (:date m))} (:name m)]))
          (:group-name m) (update :group-name (fn [monitor] [:a {:href (str prefix "/monitor/" (codec/url-encode monitor))} monitor]))
          (:average-duration m) (update :average-duration #(format "%.2f" (float %)))
          (:monitor-type m) (update :monitor-type (fn [t] [:span.tag t]))
          (:uptime m) (update :uptime #(format "%.2f" %))))

(defn pretty-headers [ks]
  (mapv {:monitor-type "Type" :target "Target" :average-duration "ms" :uptime "Up(%)"} ks))

(defn with-page
  [{:keys [vega? title] :or {vega? false title "Monitor Report"}} & body]
  (html5
   {}
   (into
    [:head
     [:meta {:charset "utf-8"}]
     [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0, user-scalable=no"}]
     [:title "Clomon"]
     [:link {:rel "stylesheet" :href (str prefix "/assets/bulma/css/bulma.min.css") :type "text/css"}]
     [:script {:src (str prefix "/assets/font-awesome/js/all.js") :type "text/javascript" :defer true}]]
    (if vega?
      [#_[:link {:rel "stylesheet" :href "http://ozviz.io/css/style.css" :type "text/css"}]
       [:script {:type "text/javascript" :src "https://cdn.jsdelivr.net/npm/vega@5"}]
       [:script {:type "text/javascript" :src "https://cdn.jsdelivr.net/npm/vega-lite@3"}]
       [:script {:type "text/javascript" :src "https://cdn.jsdelivr.net/npm/vega-embed@4"}]]))
   (into
    [:body
     [:div.navbar.has-shadow.is-spaced {:role "navigation" :aria-label "main navigation"}
      [:div.container.is-size-4
       [:div.navbar-brand [:a.navbar-item.is-size-3.has-text-weight-bold.is-family-code.is-primary
                           {:href "/"}
                           [:span.icon [:i.fas.fa-satellite-dish]]
                           [:p "&nbsp;CLOMON&nbsp;"]
                           [:span.icon [:i.fas.fa-satellite]]]]
       [:div.navbar-start [:a.navbar-item {:href "summary/"} "Summary"]]
       [:div.navbar-end
        [:p.navbar-item (str "Last synced " (t/format (tick.format/formatter "HH:mm:ss") (t/date-time)))]
        [:a.navbar-item {:href ""}  [:span.icon [:i.fas.fa-sync]]]]]]
     [:div.section
      [:div.container
       [:level
        [:div.level-item.has-text-centered
         [:div
          [:p.heading "Current View"]
          [:p.title title]]]]]]]
    body)))

(defn vega-lite-graph [spec]
  [:div
   (oz/embed [:vega-lite spec])
   [:div#vis-tooltip {:class "vg-tooltip"}]])

(defn outage-summary []
  (let [data (db/summarize-graph-data db/connection)]
    (with-page {:vega? true :title "Outage Summary for Last 7 Days"}
      (vega-lite-graph {:width 1000
                        :data {:values data}
                        ;; :transform [{:timeUnit "day" :field "date" :as "daydate"}]
                        :encoding {:x {:field "date" :timeUnit "daymonthyear" :type "ordinal"}
                                   :y {:field "target" :type "nominal"}
                                   :size {:field "downtime" :type "quantitative" :scale {:domain [0.0 100.0] :color "red"} #_:aggregate #_"sum"}
                                   :color {:field "monitor-type" :type "nominal"}}
                        :mark "circle"}))))

(defn fetch-latest-reports []
  (into cached-reports (monitor-report {:from-date (t/today)})))

(defn report []
  (with-page {:vega? true :title (t/format (tick.format/formatter "yyyy/M/dd") (t/today))}
    [:div.section
     [:div.container.content
      [:h3 "Ongoing outages"]
      (let [outages (db/ongoing-outage-summary db/connection)]
        (into
          [:ul]
          (for [{:keys [monitor-type group-name name target begin-ts duration]} outages]
            [:li [:span.tag monitor-type] (str "Incident " begin-ts " for " group-name "'s " name " (" target "): down for " duration)])))]]
    [:div.section
     [:div.container.content
      [:h3 "Past outages (5 most recent)"]
      (let [outages (take 5 (db/outage-summary db/connection))]
        (if (empty? outages)
          [:p "No outages."]
          (into
            [:ul]
            (for [{:keys [monitor-type group-name name target begin-ts end-ts duration]} outages]
              [:li [:span.tag monitor-type] (str "Incident " begin-ts "‐" end-ts " for " group-name "'s " name " (" target "): was down for " duration)]))))]]
    [:div.section
     [:div.container
      (into
       [:div.columns.is-multiline]
       (let [grouped-data (group-by :group-name (db/summarize-by-date db/connection {:date (t/today)}))]
         (for [[group data] grouped-data]
           [:div.column
            [:div.card
             [:div.card-header [:p.card-header-title group]]
             [:div.card-content
              [:div.content.is-size-7
               (let [header-keys [:monitor-type :target :average-duration :uptime]
                     body (mapv (fn [m]
                                  (into [:tr]
                                        (for [header header-keys]
                                          [:td (get (pretty-render m) header)])))
                                data)]
                 [:table
                  [:thead (into [:tr] (mapv (fn [h] [:td h]) (pretty-headers header-keys)))]
                  (into [:tbody] body)])]]
             [:div.card-image
              [:figure.image
               (vega-lite-graph {:width 200
                                 :data {:values (db/monitors-by-date db/connection {:group_name group :date (t/today)})}
                                 :encoding {:x {:field "ts" :type "temporal"}
                                            :y {:field "duration" :type "quantitative"}
                                            :color {:field "target" :type "nominal" :legend nil}}
                                 :mark "point"})]]]])))]]))

(defn target-by-date [target date]
  (let [xs (db/target-by-date db/connection {:target target :date date})]
    ;; TODO get name and group from first record...
    (with-page {:vega? true :title (str "Summary for target " target " on " date)}
      [:div.container.is-fluid
       (vega-lite-graph {:width 1200
                         :data {:values (map #(-> % (select-keys [:ts :duration :status])) xs)}
                         :encoding {:x {:field "ts" :type "temporal"}
                                    :y {:field "duration" :type "quantitative"}
                                    :color {:field "status" :type "nominal"}}
                         :mark "line"})])))

(defn monitor-summary [monitor]
  (let [reports (filter #(= monitor (:group-name %)) (fetch-latest-reports))
        header-keys (keys (first reports))
        header (into [:tr] (mapv (fn [h] [:td h]) header-keys))]
    (let [body (mapv (fn [m]
                       (into [:tr]
                             (for [header header-keys]
                               [:td (get (pretty-render m) header)])))
                     reports)]
      (with-page {}
        [:div.container.content
         [:div.row
          [:h3 monitor]
          [:table
           [:thead header]
           [:tfoot header]
           (into [:tbody] body)]]]))))

;; IDEA: summary cards that contain groupings of monitor names, or targets, with the latest data graphed within (smaller size) arranges in flex rows.
