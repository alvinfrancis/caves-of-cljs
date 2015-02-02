(ns caves-of-cljs.core
    (:require [reagent.core :as reagent :refer [atom]]
              [secretary.core :as secretary :include-macros true]
              [goog.events :as events]
              [goog.history.EventType :as EventType])
    (:import goog.History))

;; -------------------------
;; State

(def app-state (atom {}))

;; -------------------------
;; Views

(defn home-page []
  [:div [:h2 "Caves of Clojurescript"]
   [:div [:a {:href "#/about"} "go to the about page"]]])

(defn about-page []
  [:div [:h2 "About caves-of-cljs"]
   [:div [:a {:href "#/"} "go to the home page"]]])

(defmulti page #(:current-page (deref %)))

(defmethod page nil [state]
  [home-page])

(defmethod page :home [state]
  [home-page])

(defmethod page :about [state]
  [about-page])

(defn current-page [app-state]
  [:div [page app-state]])

;; -------------------------
;; Routes
(secretary/set-config! :prefix "#")

(secretary/defroute "/" []
  (swap! app-state assoc :current-page :home))

(secretary/defroute "/about" []
  (swap! app-state assoc :current-page :about))

;; -------------------------
;; History
;; must be called after routes have been defined
(defn hook-browser-navigation! []
  (doto (History.)
    (events/listen
     EventType/NAVIGATE
     (fn [event]
       (secretary/dispatch! (.-token event))))
    (.setEnabled true)))

;; -------------------------
;; Initialize app
(defn init! []
  (hook-browser-navigation!)
  (reagent/render-component [current-page app-state] (.getElementById js/document "app")))
