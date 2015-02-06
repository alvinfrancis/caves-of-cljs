(ns caves-of-cljs.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [reagent.core :as reagent :refer [atom]]
            [clojure.set :as set]
            [cljs.core.async :as async :refer [put! chan <!]]
            [secretary.core :as secretary :include-macros true]
            [goog.dom :as dom]
            [goog.events :as events]
            [goog.events.KeyCodes]
            [goog.history.EventType :as EventType]
            [caves-of-cljs.utils :as utils])
  (:import goog.History))

;; -------------------------
;; State

(defonce app-state (atom {}))

;; -------------------------
;; Game

(defonce *keys* (-> events/KeyCodes
                    (js->clj :keywordize-keys true)
                    (set/map-invert)))

(defn init-key-handler! [state target]
  (let [key-chan (utils/listen target (.-KEYDOWN events/EventType))]
    (go-loop []
      (when-let [event (<! key-chan)]
        (let [key-code (.. event -keyCode)
              input (get *keys* (if (<= 96 key-code 105)
                                  (- key-code 48)
                                  key-code))]
          (when (:game @app-state)
            (swap! state assoc-in [:game :input] input)))
        (recur)))))

(defmulti draw-ui
  (fn [ui game screen]
    (:kind ui)))

(defmethod draw-ui :start [ui game screen]
  (.drawText screen 0 0 "Welcome to the Caves of Clojure!")
  (.drawText screen 0 1 "Press enter to win, anything else to lose."))

(defmethod draw-ui :win [ui game screen]
  (.drawText screen 0 0 "Congratulations, you win!")
  (.drawText screen 0 1 "Press escape to exit, anything else to restart."))

(defmethod draw-ui :lose [ui game screen]
  (.drawText screen 0 0 "Sorry, better luck next time.")
  (.drawText screen 0 1 "Press escape to exit, anything else to go."))

(defn draw-game [game screen]
  (.clear screen)
  (doseq [ui (:uis game)]
    (draw-ui ui game screen))
  game)

(defmulti process-input
  (fn [game input]
    (:kind (last (:uis game)))))

(defmethod process-input :start [game input]
  (if (= input :ENTER)
    (assoc game :uis [{:kind :win}])
    (assoc game :uis [{:kind :lose}])))

(defmethod process-input :win [game input]
  (if (= input :ESC)
    (assoc game :uis [])
    (assoc game :uis [{:kind :start}])))

(defmethod process-input :lose [game input]
  (if (= input :ESC)
    (assoc game :uis [])
    (assoc game :uis [{:kind :start}])))

(defn handle-input [game]
  (if-let [input (:input game)]
    (-> game
        (process-input input)
        (dissoc :input))
    game))

(defn tick-game [state screen]
  (let [game (:game @state)]
    (swap! state assoc :game
           (-> game
               handle-input
               (draw-game screen)))))

(defn game-loop! [state screen]
  (tick-game state screen)
  (.requestAnimationFrame js/window #(game-loop! state screen)))

(defn init-game! [state screen]
  (init-key-handler! state (.getContainer screen))
  (game-loop! app-state screen))

;; -------------------------
;; Views

(defn canvas [state]
  (reagent/create-class
   {:component-did-mount (fn [this]
                           (let [console (js/ROT.Display.
                                          #js {:width 40
                                               :height 15})
                                 console-dom (.getContainer console)
                                 node (.getDOMNode this)]
                             (.appendChild node console-dom)
                             (dom/setProperties console-dom #js {:tabIndex 1})
                             (init-game! state console)))
    :component-function (fn [state] [:div])}))

(defn home-page [state]
  [:div [:h2 "Caves of Clojurescript"]
   [:div [:a {:href "#/about"} "go to the about page"]]
   (when-not (empty? (:uis (:game @state)))
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
         :game {:uis [{:kind :start}]}
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
  (reagent/render-component [current-page app-state] (.getElementById js/document "app")))
