(ns caves-of-cljs.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [reagent.core :as reagent :refer [atom]]
            [cljs.core.async :as async :refer [put! chan <!]]
            [secretary.core :as secretary :include-macros true]
            [goog.dom :as dom]
            [goog.events :as events]
            [goog.history.EventType :as EventType]
            [caves-of-cljs.utils :as utils])
  (:import goog.History))

;; -------------------------
;; State

(defonce app-state (atom {}))

;; -------------------------
;; Game

(defonce key-chan (utils/listen (dom/getDocument) (.-KEYPRESS events/EventType)))

(defonce key-loop
  (go-loop []
    (when-let [event (<! key-chan)]
      (swap! app-state dissoc :game)
      (recur))))

(defn draw-ui [display]
  (.drawText display 0 0 "Welcome to the Caves of Clojure!")
  (.drawText display 0 1 "Press any key to exit..."))

(defn game-loop []
  (when-let [display (:screen @app-state)]
    (let [game (:game @app-state)]
      (.clear display)
      (draw-ui display)))
  (.requestAnimationFrame js/window #(game-loop)))

(defn init-game! []
  (.requestAnimationFrame js/window #(game-loop)))

(init-game!)

;; -------------------------
;; Views

(defn canvas [state]
  (reagent/create-class
   {:component-did-mount (fn [this]
                           (let [console (js/ROT.Display.
                                          #js {:width 40
                                               :height 15})]
                             (-> this
                                 (.getDOMNode)
                                 (.appendChild (.getContainer console)))
                             (swap! state assoc :screen console)))
    :component-will-unmount (fn [this] (swap! state dissoc :screen))
    :component-function (fn [state] [:div])}))

(defn home-page [state]
  [:div [:h2 "Caves of Clojurescript"]
   [:div [:a {:href "#/about"} "go to the about page"]]
   (when (:game @state)
     [canvas state])])

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
         :game {}))

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
