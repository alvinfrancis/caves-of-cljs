(ns caves-of-cljs.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [reagent.core :as r :refer [atom]]
            [clojure.set :as set]
            [cljs.core.async :as async :refer [put! chan <!]]
            [secretary.core :as secretary :include-macros true]
            [goog.dom :as dom]
            [goog.events :as events]
            [goog.events.KeyCodes]
            [goog.history.EventType :as EventType]
            [caves-of-cljs.utils :as utils]
            [caves-of-cljs.game :as game])
  (:import goog.History))

;; -------------------------
;; State

(defonce app-state (atom {}))

;; -------------------------
;; Views

(defn home-page [state]
  [:div [:h2 "Caves of Clojurescript"]
   [:div [:a {:href "#/about"} "go to the about page"]]
   [game/view (r/wrap (:game @state) swap! state assoc :game)]])

(defn about-page []
  [:div [:h2 "About caves-of-cljs"]
   [:div [:a {:href "#/"} "go to the home page"]]])

(defmulti page #(:current-page (deref %)))

(defmethod page nil [state]
  [home-page state])

(defmethod page :home [state]
  [home-page state])

(defmethod page :about [state]
  [about-page])

(defn current-page [app-state]
  [:div [page app-state]])

;; -------------------------
;; Routes
(secretary/set-config! :prefix "#")

(secretary/defroute "/" []
  (swap! app-state assoc
         :current-page :home
         :game {:uis [{:kind :start}]
                :location [40 20]}
         :input nil))

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
  (r/render-component [current-page app-state] (.getElementById js/document "app")))
