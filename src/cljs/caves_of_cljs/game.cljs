(ns caves-of-cljs.game
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [reagent.core :as r :refer [atom]]
            [clojure.set :as set]
            [cljs.core.async :as async :refer [put! chan <!]]
            [goog.dom :as dom]
            [goog.events :as events]
            [goog.events.KeyCodes]
            [goog.history.EventType :as EventType]
            [caves-of-cljs.utils :as utils]))


;; -------------------------
;; Input

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
          (swap! state assoc :input input))
        (recur)))))

(defmulti process-input
  (fn [game input]
    (:kind (last (:uis game)))))

(defmethod process-input nil [game input]
  game)

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


;; -------------------------
;; Render

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


;; -------------------------
;; Main

(defn tick-game [game screen]
  (reset! game (-> @game
                   handle-input
                   (draw-game screen))))

(defn game-loop! [state screen]
  (tick-game state screen)
  (.requestAnimationFrame js/window #(game-loop! state screen)))

(defn init-game! [state screen]
  (init-key-handler! state (.getContainer screen))
  (game-loop! state screen))

(defn view [game]
  (r/create-class
   {:component-did-mount (fn [this]
                           (let [console (js/ROT.Display.
                                          #js {:width 40
                                               :height 15})
                                 console-dom (.getContainer console)
                                 node (.getDOMNode this)]
                             (.appendChild node console-dom)
                             (dom/setProperties console-dom #js {:tabIndex 1})
                             (init-game! game console)))
    :component-function (fn [game] [:div])}))
