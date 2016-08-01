(ns cljs-bolt-demo.core
  (:require [reagent.core :as reagent]
            [goog.object :as gobj]
            [cljsjs.neo4j]))

(def neo4j (.-v1 js/neo4j))

(defonce app-state (reagent/atom {:qry "MERGE (person:Person {name: 'Neo'}) RETURN person.name AS name;"
                                  :running? false
                                  :result nil
                                  :error  nil
                                  :url    "bolt://localhost"
                                  :username "neo4j"
                                  :passwd   "neo4j"}))

(defn get-driver
  []
  (let [data  @app-state]
    (.driver neo4j (:url data) (-> neo4j
                                   .-auth
                                   (.basic (:username data)
                                           (:passwd data))))))

(defn get-session
  [driver]
  (.session driver))

(defn run-query
  ([session qry]
   (run-query session qry {}))
  ([session qry params]
   (.run session qry (clj->js params))))

(defn get-error
  [error]
  (let [e  (aget (.-fields error) 0)]
    (str (gobj/get e "code")
         " - "
         (gobj/get e "message"))))

(defn populate-data
  [qry]
  (let [driver  (get-driver)
        session (get-session driver)
        res     (run-query session qry)]
    (-> res
        (.then (fn [result]
                 (let [xs (map (fn [x]
                                 (zipmap (.-keys x)
                                         (.-_fields x)))
                               (.-records result))]
                   (swap! app-state assoc :result xs
                          :running? false))
                 (.close session)
                 (.close driver)))
        (.catch (fn [error]
                  (let [msg  (or (.-message error)
                                 (get-error error))]
                    (swap! app-state
                           assoc :running? false
                           :error msg))
                  (js/console.log error))))))

(defn hello-world
  []
  [:div
   [:div.row
    [:h1 "CLJS<->Neo4j Bolt Demo"]
    [:form.form-inline
     [:div.form-group
      [:label "URL"]
      [:input.form-control {:type :text
                            :style {:margin-left "5px"}
                            :placeholder "Neo4j Bolt URL"
                            :on-change #(swap! app-state assoc :url (-> % .-target .-value))
                            :value (:url @app-state)}]]
     [:div.form-group {:style {:margin-left "10px"}}
      [:label "Username"]
      [:input.form-control {:type :text
                            :placeholder "Username"
                            :style {:margin-left "5px"}
                            :on-change #(swap! app-state assoc :username (-> % .-target .-value))
                            :value (:username @app-state)}]]
     [:div.form-group {:style {:margin-left "10px"}}
      [:label "Password"]
      [:input.form-control {:type :password
                            :placeholder "password"
                            :style {:margin-left "5px"}
                            :on-change #(swap! app-state assoc :passwd (-> % .-target .-value))
                            :value (:passwd @app-state)}]]]
    [:h2 "Query"]]
   [:div.row {:style {:margin-top "6px"}}
    [:div.col-md-8
     [:textarea.form-control {:rows 5 :cols 50
                              :value (:qry @app-state)
                              :on-change #(swap! app-state assoc :qry (-> % .-target .-value))}]]
    [:div.col-md-2
     [:button.btn.btn-primary.btn-lg
      {:type :button
       :on-click (fn []
                   (swap! app-state
                          assoc :running? true
                          :result nil
                          :error  nil)
                   (populate-data (:qry @app-state)))}
      (if (:running? @app-state)
        [:span "Working!"
         [:i.fa.fa-spinner]]
        [:span "Submit!"])]]]
   (when (:error @app-state)
     [:div.row {:style {:margin-top "5px"}}
      [:div.alert.alert-danger
       [:strong "Error!  "]
       (:error @app-state)]])
   [:div
    (when-let [data   (:result @app-state)]
      (let [header (keys (first data))]
        [:h2 "Results"]
        [:table.table {:style {:margin.top "20px"}}
         [:thead
          [:tr
           (for [h  header]
             ^{:key h} [:th h])]]
         (into
          [:tbody]
          (for [row  data]
            [:tr
             (for [h header]
               ^{:key h} [:td (get row h)])]))]))]])

(reagent/render-component [hello-world]
                          (. js/document (getElementById "app")))

(defn on-js-reload
  [])
