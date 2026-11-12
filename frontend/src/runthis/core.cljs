(ns runthis.core
  (:require
   [ajax.core :as ajax]
   [reagent.core :as reagent]
   [reagent.dom :as dom]))

(defonce state-atom
  (reagent/atom {:build-keys []
                 :runs []
                 :selected-run nil
                 :output ""
                 :output-offset 0
                 :error nil}))

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

(defn fetch-builds []
  (call-api "/api/builds"
            (fn [{:keys [build-keys runs]}]
              (swap! state-atom assoc
                     :build-keys build-keys
                     :runs runs))
            {:error-handler (fn [_error]
                              (swap! state-atom assoc
                                     :error "could not load builds"))}))

(defn fetch-output [run]
  (call-api "/api/build-output"
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
                  (fetch-builds))))
            {:body {:key (:key run)
                    :number (:number run)
                    :offset (:output-offset @state-atom)}
             :error-handler (fn [_error]
                              (stop-polling)
                              (swap! state-atom assoc
                                     :error "could not load build output"))}))

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

(defn start-build [key]
  (stop-polling)
  (swap! state-atom assoc :error nil)
  (call-api "/api/start-build"
            (fn [run]
              (swap! state-atom assoc
                     :selected-run run
                     :output ""
                     :output-offset 0)
              (fetch-builds)
              (start-polling run)
              (fetch-output run))
            {:body {:key key}
             :error-handler (fn [_error]
                              (swap! state-atom assoc
                                     :error "could not start build"))}))

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

(defn build-script-row [key]
  [:div {:style {:display "flex"
                 :justify-content "space-between"
                 :align-items "center"
                 :gap "10px"
                 :padding "8px"
                 :border "1px solid lightgray"
                 :border-radius "8px"}}
   [:strong (name key)]
   [button "Run"
    (fn []
      (start-build key))]])

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

(defn root-view []
  (let [{:keys [build-keys runs selected-run output error]} @state-atom]
    [:div {:style {:display "flex"
                   :flex-direction "column"
                   :align-items "center"
                   :gap "20px"
                   :margin "30px"
                   :font-family "arial"}}
     [:h1 "Runthis"]

     (when error
       [:div {:style {:color "red"}}
        error])

     [:div {:style {:display "flex"
                    :gap "30px"
                    :align-items "flex-start"
                    :width "100%"
                    :max-width "1100px"}}
      [:div {:style {:flex "1"
                     :display "flex"
                     :flex-direction "column"
                     :gap "10px"}}
       [:h2 "Build scripts"]
       (if (empty? build-keys)
         [:div "No build scripts registered."]
         (for [key build-keys]
           ^{:key (name key)}
           [build-script-row key]))

       [:h2 "Builds"]
       (if (empty? runs)
         [:div "No builds yet."]
         (for [run runs]
           ^{:key (str (name (:key run)) "-" (:number run))}
           [run-row run selected-run]))]

      [:div {:style {:flex "2"
                     :display "flex"
                     :flex-direction "column"
                     :gap "10px"}}
       [:h2 (if selected-run
              (str (name (:key selected-run)) " #" (:number selected-run))
              "Build log")]
       [output-view output]]]]))

;; this is configured as :after-load in shadow-cljs.edn
;; to refresh the page after each change
(defn mount-root []
  (dom/render [root-view]
              (.getElementById js/document "app")))

(defn ^:export init! []
  (mount-root)
  (fetch-builds))
