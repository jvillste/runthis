(ns runthis.core
  (:require
   [ajax.core :as ajax]
   [reagent.core :as reagent]
   [reagent.dom :as dom]
   [reitit.frontend :as frontend]
   [reitit.frontend.easy :as easy]))

(defonce state-atom
  (reagent/atom {:task-keys []
                 :runs []
                 :selected-run nil
                 :output ""
                 :output-offset 0
                 :error nil}))

(defonce match-atom (reagent/atom nil))
(defonce poll-timer-atom (atom nil))

(defn call-api [uri handler & [{:keys [body error-handler]}]]
  (ajax/POST uri
             {:format :transit
              :handler handler
              :error-handler (or error-handler
                                 (fn [_error]
                                   (swap! state-atom assoc
                                          :error "request failed")))
              :params body}))

(defn same-run? [first-run second-run]
  (and (some? first-run)
       (= (:key first-run) (:key second-run))
       (= (:number first-run) (:number second-run))))

(defn stop-polling []
  (when-some [timer @poll-timer-atom]
    (js/clearInterval timer)
    (reset! poll-timer-atom nil)))

(defn fetch-runs []
  (call-api "/api/runs"
            (fn [{:keys [task-keys runs]}]
              (swap! state-atom assoc
                     :task-keys task-keys
                     :runs runs))
            {:error-handler (fn [_error]
                              (swap! state-atom assoc
                                     :error "could not load runs"))}))

(defn fetch-output [run]
  (call-api "/api/run-output"
            (fn [{:keys [output next-offset status]}]
              (when (same-run? run (:selected-run @state-atom))
                (swap! state-atom
                       (fn [state]
                         (-> state
                             (update :output str output)
                             (assoc :output-offset next-offset)
                             (assoc-in [:selected-run :status] status))))
                (when (not= :running status)
                  (stop-polling)
                  (fetch-runs))))
            {:body {:key (:key run)
                    :number (:number run)
                    :offset (:output-offset @state-atom)}
             :error-handler (fn [_error]
                              (stop-polling)
                              (swap! state-atom assoc
                                     :error "could not load run output"))}))

(defn start-polling [run]
  (stop-polling)
  (reset! poll-timer-atom
          (js/setInterval (fn []
                            (fetch-output run))
                          500)))

(defn select-run [run]
  (stop-polling)
  (swap! state-atom assoc
         :selected-run run
         :output ""
         :output-offset 0
         :error nil)
  (when (= :running (:status run))
    (start-polling run))
  (fetch-output run))

(defn start-run [key]
  (stop-polling)
  (swap! state-atom assoc :error nil)
  (call-api "/api/start-run"
            (fn [run]
              (swap! state-atom assoc
                     :selected-run run
                     :output ""
                     :output-offset 0)
              (fetch-runs)
              (start-polling run)
              (fetch-output run))
            {:body {:key key}
             :error-handler (fn [_error]
                              (swap! state-atom assoc
                                     :error "could not start run"))}))

(defn task-key-from-match [match]
  (when-some [task-key-string (get-in match [:parameters :path :task-key])]
    (keyword task-key-string)))

(defn initialize-home-page [_match]
  (stop-polling))

(defn initialize-task-page [match]
  (stop-polling)
  (let [task-key (task-key-from-match match)
        selected-run (:selected-run @state-atom)]
    (if (= task-key (:key selected-run))
      (when (= :running (:status selected-run))
        (start-polling selected-run))
      (swap! state-atom assoc
             :selected-run nil
             :output ""
             :output-offset 0))))

(defn button [label on-click]
  [:div {:style {:background "lightgray"
                 :padding "10px"
                 :cursor "pointer"
                 :border-radius "10px"
                 :width "fit-content"}
         :onClick on-click}
   label])

(defn status-label [status]
  (case status
    :running "running"
    :succeeded "succeeded"
    :failed "failed"
    "finished"))

(defn task-link [key]
  [:a {:href (easy/href ::task {:task-key (name key)})
       :style {:display "block"
               :padding "12px"
               :border "1px solid lightgray"
               :border-radius "8px"
               :color "inherit"
               :text-decoration "none"}}
   [:strong (name key)]])

(defn run-row [run selected-run]
  [:div {:style (merge {:display "flex"
                        :justify-content "space-between"
                        :gap "10px"
                        :padding "8px"
                        :border "1px solid lightgray"
                        :border-radius "8px"
                        :cursor "pointer"}
                       (when (same-run? run selected-run)
                         {:background "lightblue"}))
         :onClick (fn []
                    (select-run run))}
   [:div (str (name (:key run)) " #" (:number run))]
   [:div {:style {:color "gray"}}
    (status-label (:status run))]])

(defn output-view [output]
  [:pre {:style {:background "#111"
                 :color "#eee"
                 :padding "10px"
                 :min-height "300px"
                 :max-height "600px"
                 :overflow "auto"
                 :white-space "pre-wrap"
                 :font-size "12px"}}
   output])

(defn home-page [_match]
  (let [{:keys [task-keys]} @state-atom]
    [:div {:style {:display "flex"
                   :flex-direction "column"
                   :gap "10px"
                   :width "100%"
                   :max-width "700px"}}
     [:h1 "Tasks"]
     (if (empty? task-keys)
       [:div "No tasks registered."]
       (for [key task-keys]
         ^{:key (name key)}
         [task-link key]))]))

(defn task-page [match]
  (let [task-key (task-key-from-match match)
        {:keys [task-keys runs selected-run output]} @state-atom
        task-exists? (some (fn [key]
                             (= task-key key))
                           task-keys)
        task-runs (filter (fn [run]
                            (= task-key (:key run)))
                          runs)
        selected-run (when (and (some? selected-run)
                                (= task-key (:key selected-run)))
                       selected-run)
        output (if selected-run
                 output
                 "")]
    [:div {:style {:display "flex"
                   :flex-direction "column"
                   :gap "20px"
                   :width "100%"
                   :max-width "1100px"}}
     [:div {:style {:font-size "18px"}}
      [:a {:href (easy/href ::home)
           :style {:color "inherit"}}
       "home"]
      "/"
      (name task-key)]

     (if task-exists?
       [:div {:style {:display "flex"
                      :flex-direction "column"
                      :gap "20px"}}
        [:div {:style {:display "flex"
                       :justify-content "space-between"
                       :align-items "center"}}
         [:h1 (name task-key)]
         [button "Run"
          (fn []
            (start-run task-key))]]

        [:div {:style {:display "flex"
                       :gap "30px"
                       :align-items "flex-start"}}
         [:div {:style {:flex "1"
                        :display "flex"
                        :flex-direction "column"
                        :gap "10px"}}
          [:h2 "Runs"]
          (if (empty? task-runs)
            [:div "No runs yet."]
            (for [run task-runs]
              ^{:key (str (name (:key run)) "-" (:number run))}
              [run-row run selected-run]))]

         [:div {:style {:flex "2"
                        :display "flex"
                        :flex-direction "column"
                        :gap "10px"}}
          [:h2 "Run log"]
          [output-view output]]]]

       [:div "Task not found."])]))

(def routes
  [["/" {:name ::home
         :view home-page
         :initialize initialize-home-page}]
   ["/task/:task-key" {:name ::task
                       :view task-page
                       :initialize initialize-task-page}]])

(defn root-view []
  (let [{:keys [error]} @state-atom
        match @match-atom]
    [:div {:style {:display "flex"
                   :flex-direction "column"
                   :align-items "center"
                   :gap "20px"
                   :margin "30px"
                   :font-family "arial"}}
     (when error
       [:div {:style {:color "red"}}
        error])
     (when match
       (let [view (:view (:data match))]
         [view match]))]))

;; this is configured as :after-load in shadow-cljs.edn
;; to refresh the page after each change
(defn mount-root []
  (dom/render [root-view]
              (.getElementById js/document "app")))

(defn ^:export init! []
  (easy/start! (frontend/router routes {:conflicts nil})
               (fn [match]
                 (reset! match-atom match)
                 (when-some [initialize (-> match :data :initialize)]
                   (initialize match)))
               {:use-fragment false})
  (mount-root)
  (fetch-runs))
