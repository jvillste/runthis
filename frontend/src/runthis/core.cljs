(ns runthis.core
  (:require
   [ajax.core :as ajax]
   [reagent.core :as reagent]
   [reagent.dom :as dom]))

(defonce state-atom (reagent/atom {:name ""
                                   :message nil
                                   :loading? false}))

(defn call-api [uri handler & [{:keys [body error-handler]}]]
  (ajax/POST uri
             {:format :transit
              :handler handler
              :error-handler (or error-handler
                                 (fn [_error]
                                   (swap! state-atom assoc
                                          :message "request failed"
                                          :loading? false)))
              :params body}))

(defn fetch-hello [name]
  (swap! state-atom assoc :loading? true)
  (call-api "/api/get-hello"
            (fn [message]
              (swap! state-atom assoc
                     :message message
                     :loading? false))
            {:body {:name name}}))

(defn button [label on-click]
  [:div {:style {:background "lightgray"
                 :padding "10px"
                 :cursor "pointer"
                 :border-radius "10px"
                 :width "fit-content"}
         :onClick on-click}
   label])

(defn root-view []
  (let [{:keys [name message loading?]} @state-atom]
    [:div {:style {:display "flex"
                   :flex-direction "column"
                   :align-items "center"
                   :gap "20px"
                   :margin-top "50px"
                   :font-family "arial"}}
     [:h1 "CI server"]

     [:div {:style {:display "flex"
                    :align-items "center"
                    :gap "10px"}}
      [:input {:type "text"
               :placeholder "name"
               :value name
               :on-change (fn [event]
                            (swap! state-atom assoc
                                   :name (.-value (.-target event))))
               :on-key-down (fn [event]
                              (when (= "Enter" (.-key event))
                                (fetch-hello name)))}]
      [button "Say hello"
       (fn []
         (fetch-hello name))]]

     (when loading?
       [:div "Loading..."])

     (when message
       [:div {:style {:font-size "24px"}}
        message])]))

;; this is configured as :after-load in shadow-cljs.edn
;; to refresh the page after each change
(defn mount-root []
  (dom/render [root-view]
              (.getElementById js/document "app")))

(defn ^:export init! []
  (mount-root))
