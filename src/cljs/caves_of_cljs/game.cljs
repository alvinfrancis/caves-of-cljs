(ns caves-of-cljs.game
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [reagent.core :as r :refer [atom]]
            [clojure.set :as set]
            [cljs.core.async :as async :refer [put! chan <!]]
            [goog.dom :as dom]
            [goog.events :as events]
            [goog.events.KeyCodes]
            [goog.history.EventType :as EventType]
            [caves-of-cljs.utils :as utils]
            [caves-of-cljs.world :as world]))

;; -------------------------
;; Constants

(defonce ^:dynamic screen-size {:cols 80 :rows 24})

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

(defn move [[x y] [dx dy]]
  [(+ x dx) (+ y dy)])

(defmulti process-input
  (fn [game input]
    (:kind (last (:uis game)))))

(defmethod process-input nil [game input]
  game)

(defmethod process-input :start [game input]
  (-> game
      (assoc :world (world/random-world))
      (assoc :uis [{:kind :play}])))

(defmethod process-input :win [game input]
  (if (= input :ESC)
    (assoc game :uis [])
    (assoc game :uis [{:kind :start}])))

(defmethod process-input :lose [game input]
  (if (= input :ESC)
    (assoc game :uis [])
    (assoc game :uis [{:kind :start}])))

(defmethod process-input :play [game input]
  (case input
    :ENTER     (assoc game :uis [{:kind :win}])
    :BACKSPACE (assoc game :uis [{:kind :lose}])
    :S         (assoc game :world (world/smooth-world (:world game)))

    :H (update-in game [:location] move [-1 0])
    :J (update-in game [:location] move [0 1])
    :K (update-in game [:location] move [0 -1])
    :L (update-in game [:location] move [1 0])

    game))

(defn handle-input [game]
  (if-let [input (:input game)]
    (-> game
        (process-input input)
        (dissoc :input))
    game))


;; -------------------------
;; Render

(defn draw-world [screen vrows vcols start-x start-y end-x end-y tiles]
  (doseq [[vrow-idx mrow-idx] (map vector
                                   (range 0 vrows)
                                   (range start-y end-y))
          :let [row-tiles (subvec (tiles mrow-idx) start-x end-x)]]
    (doseq [vcol-idx (range vcols)
            :let [{:keys [glyph color]} (row-tiles vcol-idx)]]
      (.draw screen vcol-idx vrow-idx glyph))))

(defn draw-crosshairs [screen vcols vrows]
  (let [crosshair-x (int (/ vcols 2))
        crosshair-y (int (/ vrows 2))]
    (.drawText screen crosshair-x crosshair-y "%c{red}X")))

(defn get-viewport-coords [game vcols vrows]
  (let [location (:location game)
        [center-x center-y] location
        tiles (:tiles (:world game))
        map-rows (count tiles)
        map-cols (count (first tiles))
        start-x (max 0 (- center-x (int (/ vcols 2))))
        start-y (max 0 (- center-y (int (/ vrows 2))))
        end-x (+ start-x vcols)
        end-x (min end-x map-cols)
        end-y (+ start-y vrows)
        end-y (min end-y map-rows)
        start-x (- end-x vcols)
        start-y (- end-y vrows)]
    [start-x start-y end-x end-y]))

(defmulti draw-ui
  (fn [ui game screen]
    (:kind ui)))

(defmethod draw-ui :start [ui game screen]
  (.drawText screen 0 0 "Welcome to the Caves of Clojure!")
  (.drawText screen 0 1 "Press anything to continue."))

(defmethod draw-ui :win [ui game screen]
  (.drawText screen 0 0 "Congratulations, you win!")
  (.drawText screen 0 1 "Press escape to exit, anything else to restart."))

(defmethod draw-ui :lose [ui game screen]
  (.drawText screen 0 0 "Sorry, better luck next time.")
  (.drawText screen 0 1 "Press escape to exit, anything else to go."))

(defmethod draw-ui :play [ui {{:keys [tiles]} :world :as game} screen]
  (let [world (:world game)
        tiles (:tiles world)
        {:keys [cols rows]} screen-size
        vcols cols
        vrows (dec rows)
        [start-x start-y end-x end-y] (get-viewport-coords game vcols vrows)]
    (draw-world screen vrows vcols start-x start-y end-x end-y tiles)
    (draw-crosshairs screen vcols vrows)))

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
                                          #js {:width (:cols screen-size)
                                               :height (:rows screen-size)})
                                 console-dom (.getContainer console)
                                 node (.getDOMNode this)]
                             (.appendChild node console-dom)
                             (dom/setProperties console-dom #js {:tabIndex 1})
                             (init-game! game console)))
    :component-function (fn [game] [:div])}))
