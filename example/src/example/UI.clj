;; This software is copyright 2016, 2017, 2018, 2019 by Marshall Abrams, 
;; and is distributed under the Gnu General Public License version 3.0 
;; as specified in the the file LICENSE.

;(set! *warn-on-reflection* true)

(ns example.UI
  (:require [example.Sim :as sim]
            [clojure.math.numeric-tower :as math])
  (:import [example snipe Sim] ; mush 
           [sim.engine Steppable Schedule Stoppable]
           [sim.field.grid ObjectGrid2D] ; normally doesn't belong in UI: a hack to use a field portrayal to display a background pattern
           [sim.portrayal DrawInfo2D SimpleInspector SimplePortrayal2D]
           [sim.portrayal.grid HexaObjectGridPortrayal2D]; FastHexaObjectGridPortrayal2D ObjectGridPortrayal2D
           [sim.portrayal.simple OvalPortrayal2D RectanglePortrayal2D CircledPortrayal2D ShapePortrayal2D OrientedPortrayal2D FacetedPortrayal2D] ; HexagonalPortrayal2D 
           [sim.display Console Display2D]
           [java.awt.geom Rectangle2D$Double] ; note wierd Clojure syntax for Java static nested class
           [java.awt Color])
  (:gen-class
    :name example.UI
    :extends sim.display.GUIState
    :main true
    :exposes {state {:get getState}}  ; accessor for field in superclass that will contain my Sim after main creates instances of this class with it.
    :exposes-methods {start superStart, quit superQuit, init superInit, getInspector superGetInspector}
    :state getUIState
    :init init-instance-state))

;; NOTE:
;; OrientedPortrayal2D shape options:
;; SHAPE_LINE, SHAPE_KITE, SHAPE_COMPASS
;; not in javadoc:
;; SHAPE_LINE_ARROW, SHAPE_ARROW, SHAPE_TRIANGLE, SHAPE_INVERTED_T
;; However these are difficult to configure appropriately. Weird interactions between offset and scale.

;; display parameters:
;; white background:
;(def bg-color (Color. 255 255 255))   ; color of background without grid (if show-grid is false)
(def display-backdrop-color (Color. 64 64 64)) ; border around subenvs
;(def superimposed-subenv-offset 3.5)
(def snipe-size 0.55)
(defn snipe-shade-fn [max-energy snipe] (int (+ 64 (* 190 (/ (:energy snipe) max-energy)))))
;(defn snipe-shade-fn [max-energy snipe]   ; DEBUG VERSION
;  (let [shade (int (+ 64 (* 190 (/ (:energy snipe) max-energy))))]
;    (when (> shade 255)
;      (println "SHADE:" shade max-energy (dissoc snipe :sim-data$)))
;    shade))
(defn k-snipe-color-fn [max-energy snipe] (Color. (snipe-shade-fn max-energy snipe) 0 0))
(defn r-snipe-color-fn [max-energy snipe] (Color. 0 0 (snipe-shade-fn max-energy snipe)))
(defn s-snipe-color-fn [max-energy snipe] (Color. (snipe-shade-fn max-energy snipe) 0 (snipe-shade-fn max-energy snipe)))
;(defn s-snipe-color-fn [max-energy snipe] (Color. 0 (snipe-shade-fn max-energy snipe) 0))
;(def mush-pos-nutrition-shade 150)
;(def mush-neg-nutrition-shade 200)
;(defn west-mush-color-fn 
;  ([shade] (Color. shade shade (int (* 0.6 shade))))
;  ([shade alpha] (Color. shade shade (int (* 0.6 shade)) alpha)))
;(defn east-mush-color-fn 
;  ([shade] (Color. shade shade shade))
;  ([shade alpha] (Color. shade shade shade alpha)))
;(def mush-high-size-appearance 1.0) ; we don't scale mushroom size to modeled size, but
;(def mush-low-size-appearance 0.875) ; we display the low-size mushroom smaller
(def org-offset 0.6) ; with simple hex portrayals to display grid, organisms off center; pass this to DrawInfo2D to correct.

(defn -init-instance-state
  [& args]
  [(vec args) {:west-display (atom nil)       ; will be replaced in init because we need to pass the UI instance to it
               :west-display-frame (atom nil) ; will be replaced in init because we need to pass the display to it
               ;:east-display (atom nil)       ; ditto
               ;:east-display-frame (atom nil) ;ditto
               ;:superimposed-display (atom nil) ; ditto
               ;:superimposed-display-frame (atom nil) ; ditto
               :west-snipe-field-portrayal (HexaObjectGridPortrayal2D.)
               ;:west-mush-field-portrayal (HexaObjectGridPortrayal2D.)
               ;:shady-east-mush-field-portrayal (HexaObjectGridPortrayal2D.)
               ;:east-snipe-field-portrayal (HexaObjectGridPortrayal2D.)
               ;:east-mush-field-portrayal (HexaObjectGridPortrayal2D.)
               }])

;; see doc/getName.md
(defn -getName-void [this] "pasta") ; override method in super. Should cause this string to be displayed as title of config window of gui, but it doesn't.

;; Override methods in sim.display.GUIState so that UI can make graphs, etc.
(defn -getSimulationInspectedObject [this] (.state this))
;; This makes the controls for the sim state in the Model tab (and does other things?):
(defn -getInspector [this]
  (let [i (.superGetInspector this)]
    (.setVolatile i true)
    i))

;;;;;;;;;;;;;;;;;;;;

(declare setup-portrayals)

(defn -main
  [& args]
  (let [sim (Sim. (System/currentTimeMillis))]  ; CREATE AN INSTANCE OF my Sim
    ;(sim/record-commandline-args! args) 
    (when @sim/commandline$ (sim/set-sim-data-from-commandline! sim sim/commandline$)) ; we can do this in -main because we have a Sim
    (swap! (.simData sim) assoc :in-gui true) ; allow functions in Sim to check whether GUI is running
    (.setVisible (Console. (example.UI. sim)) true)))  ; THIS IS WHAT CONNECTS THE GUI TO my SimState subclass Sim

(defn mein
  "Externally available wrapper for -main."
  [args]
  (apply -main args)) ; have to use apply since already in a seq

;; This is called by the pause and go buttons when starting from fully stopped.
(defn -start
  [this-ui]
  (.superStart this-ui) ; this will call start() on the sim, i.e. in our SimState object
  (setup-portrayals this-ui))

;; Possibly also define a load() method. See manual.

(defn make-fnl-circled-portrayal
  "Create a subclass of CircledPortrayal2D that tracks snipes by id
  rather than by pointer identity."
  [color child-portrayal]
  (proxy [CircledPortrayal2D] [child-portrayal color false]
    (draw [snipe graphics info]
      (.setCircleShowing this @(:circled$ snipe))
      (proxy-super draw snipe graphics info))))

(defn setup-portrayals
  [this-ui]  ; instead of 'this': avoid confusion with e.g. proxy below
  (let [sim (.getState this-ui)
        ui-config (.getUIState this-ui)
        sim-data$ (.simData sim)
        rng (.random sim)
        sim-data @sim-data$
        popenv (:popenv sim-data)
        west (:west popenv)
        ;east (:east popenv)
        max-energy (:max-energy sim-data)
        birth-threshold (:birth-threshold sim-data)
        ;mush-pos-nutrition (:mush-pos-nutrition sim-data)
        ;mush-high-size (:mush-high-size sim-data)
        effective-max-energy (min birth-threshold max-energy)
        west-display @(:west-display ui-config)
        ;east-display @(:east-display ui-config)
        ;superimposed-display @(:superimposed-display ui-config)
        ;; These portrayals should be local to setup-portrayals because proxy needs to capture the correct 'this', and we need sim-data:
        ;; Different mushroom portrayals for west and east environments:
        ;west-mush-portrayal (proxy [OvalPortrayal2D] []
        ;                      (draw [mush graphics info]  ; override method in super
        ;                        (let [size  (if (= mush-high-size (:size mush)) mush-high-size-appearance mush-low-size-appearance)
        ;                              shade (if (neg? (:nutrition mush)) mush-neg-nutrition-shade mush-pos-nutrition-shade)]
        ;                          (set! (.-scale this) size)                       ; superclass vars
        ;                          (set! (.-paint this) (west-mush-color-fn shade))
        ;                          (proxy-super draw mush graphics (DrawInfo2D. info org-offset org-offset))))) ; last arg centers organism in hex cell
        ;east-mush-portrayal (proxy [OvalPortrayal2D] []
        ;                      (draw [mush graphics info]  ; override method in super
        ;                        (let [size  (if (= mush-high-size (:size mush)) mush-high-size-appearance mush-low-size-appearance)
        ;                              shade (if (neg? (:nutrition mush)) mush-neg-nutrition-shade mush-pos-nutrition-shade)]
        ;                          (set! (.-scale this) size)                       ; superclass vars
        ;                          (set! (.-paint this) (east-mush-color-fn shade))
        ;                          (proxy-super draw mush graphics (DrawInfo2D. info org-offset org-offset))))) ; last arg centers organism in hex cell
        ;;; In the overlapping-environments display, make the mushrooms on the upper layer semi-translucent:
        ;shady-east-mush-portrayal (proxy [OvalPortrayal2D] []
        ;                            (draw [mush graphics info]  ; override method in super
        ;                              (let [size  (if (= mush-high-size (:size mush)) mush-high-size-appearance mush-low-size-appearance)
        ;                                    shade (if (neg? (:nutrition mush)) mush-neg-nutrition-shade mush-pos-nutrition-shade)]
        ;                                (set! (.-scale this) size)                       ; superclass vars
        ;                                (set! (.-paint this) (east-mush-color-fn shade 200))
        ;                                (proxy-super draw mush graphics (DrawInfo2D. info org-offset org-offset))))) ; last arg centers organism in hex cell
        ;; r-snipes are displayed with one of two different shapes
        r-snipe-portrayal (make-fnl-circled-portrayal Color/blue
                             ;; FacetedPortrayal2D chooses which of several portrayals to use:
                             (proxy [FacetedPortrayal2D] [(into-array ; make array of ShapePortrayal2Ds
                                                             ;; up triangle portrayal:
                                                             [(proxy [ShapePortrayal2D][ShapePortrayal2D/X_POINTS_TRIANGLE_UP
                                                                                        ShapePortrayal2D/Y_POINTS_TRIANGLE_UP
                                                                                        (* 1.1 snipe-size)]
                                                                (draw [snipe graphics info]
                                                                  (set! (.-paint this) (r-snipe-color-fn effective-max-energy snipe)) ; paint var is in superclass
                                                                  (proxy-super draw snipe graphics (DrawInfo2D. info (* 0.75 org-offset) (* 0.55 org-offset))))) ; see above re last arg
                                                              ;; down triangle portrayal:
                                                              (proxy [ShapePortrayal2D] [ShapePortrayal2D/X_POINTS_TRIANGLE_DOWN
                                                                                         ShapePortrayal2D/Y_POINTS_TRIANGLE_DOWN
                                                                                         (* 1.1 snipe-size)]
                                                                (draw [snipe graphics info]
                                                                  (set! (.-paint this) (r-snipe-color-fn effective-max-energy snipe))
                                                                  (proxy-super draw snipe graphics (DrawInfo2D. info (* 0.75 org-offset) (* 0.55 org-offset)))))])] ; end of constructor arg
                                (getChildIndex [snipe idxs] (if (pos? (:mush-pref snipe)) 0 1)))) ; determines which ShapePortrayal2D is chosen
        ;; k-snipes and s-snipes include pointers to display mush-prefs:
        k-snipe-portrayal (make-fnl-circled-portrayal Color/red
                             (OrientedPortrayal2D.
                               (proxy [OvalPortrayal2D] [(* 1.1 snipe-size)]
                                 (draw [snipe graphics info] ; override method in super
                                   (set! (.-paint this) (k-snipe-color-fn effective-max-energy snipe)) ; superclass var
                                   (proxy-super draw snipe graphics (DrawInfo2D. info org-offset org-offset)))) ; see above re last arg
                               0 0.6 Color/red OrientedPortrayal2D/SHAPE_LINE))
        ;; This s-snipe is just an OrientedPortrayal2D on an (undrawn) SimplePortrayal2D (p. 226 of v19 manual):
        s-snipe-portrayal (make-fnl-circled-portrayal Color/red
                             (proxy [OrientedPortrayal2D] [(SimplePortrayal2D.) 0 0.425 Color/black OrientedPortrayal2D/SHAPE_KITE] ; color will be overridden
                               (draw [snipe graphics info] ; override method in super
                                 (set! (.-paint this) (s-snipe-color-fn effective-max-energy snipe)) ; superclass var
                                 (proxy-super draw snipe graphics (DrawInfo2D. info org-offset org-offset))))) ; see above re last arg
        west-snipe-field-portrayal (:west-snipe-field-portrayal ui-config)
        ;east-snipe-field-portrayal (:east-snipe-field-portrayal ui-config)
        ;west-mush-field-portrayal (:west-mush-field-portrayal ui-config)
        ;shady-east-mush-field-portrayal (:shady-east-mush-field-portrayal ui-config)
        ;east-mush-field-portrayal (:east-mush-field-portrayal ui-config)
        ]
    ;; connect fields to their portrayals
    ;(.setField west-mush-field-portrayal (:mush-field west))
    ;(.setField east-mush-field-portrayal (:mush-field east))
    ;(.setField shady-east-mush-field-portrayal (:mush-field east))
    (.setField west-snipe-field-portrayal (:snipe-field west))
    ;(.setField east-snipe-field-portrayal (:snipe-field east))
    ;; connect portrayals to agents:
    ;; mushs:
    ;(.setPortrayalForClass west-mush-field-portrayal example.mush.Mush west-mush-portrayal)
    ;(.setPortrayalForClass east-mush-field-portrayal example.mush.Mush east-mush-portrayal)
    ;(.setPortrayalForClass shady-east-mush-field-portrayal example.mush.Mush shady-east-mush-portrayal)
    ;; west snipes:
    (.setPortrayalForClass west-snipe-field-portrayal example.snipe.KSnipe k-snipe-portrayal)
    (.setPortrayalForClass west-snipe-field-portrayal example.snipe.RSnipe r-snipe-portrayal)
    (.setPortrayalForClass west-snipe-field-portrayal example.snipe.SSnipe s-snipe-portrayal)
    ;; east snipes:
    ;(.setPortrayalForClass east-snipe-field-portrayal example.snipe.KSnipe k-snipe-portrayal)
    ;(.setPortrayalForClass east-snipe-field-portrayal example.snipe.RSnipe r-snipe-portrayal)
    ;(.setPortrayalForClass east-snipe-field-portrayal example.snipe.SSnipe s-snipe-portrayal)
    ;; Since popenvs are updated functionally, have to tell the ui about the new popenv on every timestep:
    (.scheduleRepeatingImmediatelyAfter this-ui
                                        (reify Steppable 
                                          (step [this sim-state]
                                            (let [{:keys [west ;east
                                                          ]} (:popenv @sim-data$)]
                                              (.setField west-snipe-field-portrayal (:snipe-field west))
                                              ;(.setField east-snipe-field-portrayal (:snipe-field east))
                                              ;(.setField west-mush-field-portrayal (:mush-field west))
                                              ;(.setField east-mush-field-portrayal (:mush-field east))
                                              ;(.setField shady-east-mush-field-portrayal (:mush-field east))
                                              ))))
    ;; set up display:
    (doto west-display         (.reset) (.repaint))
    ;(doto east-display         (.reset) (.repaint))
    ;(doto superimposed-display (.reset) (.repaint))
))

;; For hex grid, need to rescale display (based on HexaBugsWithUI.java around line 200 in Mason 19):
(defn hex-scale-height
  [height]
  (+ 0.5 height))
(defn hex-scale-width
  [width] 
  (* (/ 2.0 (math/sqrt 3)) 
     (+ 1 (* (- width 1)
             (/ 3.0 4.0)))))

(defn setup-display
  "Creates and configures a display and returns it."
  [ui width height]
  (let [display (Display2D. width height ui)]
    (.setClipping display false)
    display))

(defn setup-display-frame
  "Creates and configures a display-frame and returns it."
  [display controller title visible?]
  (let [display-frame (.createFrame display)]
    (.registerFrame controller display-frame)
    (.setTitle display-frame title)
    (.setVisible display-frame visible?)
    display-frame))

;; Remember: Order of attaching sets layering: Later attachments appear on top of earlier ones.
(defn attach-portrayals!
  "Attach field-portrayals in portrayals-with-labels to display with upper left corner 
  at x y in display and with width and height.  Order of portrayals determines
  how their layered, with earlier portrayals under later ones."
  [display portrayals-with-labels x y field-width field-height]
  (doseq [[portrayal label] portrayals-with-labels]
    (.attach display portrayal label
             (Rectangle2D$Double. x y field-width field-height)))) ; note Clojure $ syntax for Java static nested classes

(defn -init
  [this controller] ; fyi controller is called c in Java version
  (.superInit this controller)
  (let [sim (.getState this)
        ui-config (.getUIState this)
        sim-data @(.simData sim) ; just for env dimensions
        display-size (:env-display-size sim-data)
        width  (hex-scale-width  (int (* display-size (:env-width sim-data))))
        height (hex-scale-height (int (* display-size (:env-height sim-data))))
        ;bg-field-portrayal (:bg-field-portrayal ui-config)
        ;west-mush-field-portrayal (:west-mush-field-portrayal ui-config)
        ;shady-west-mush-field-portrayal (:shady-west-mush-field-portrayal ui-config)
        ;shady-east-mush-field-portrayal (:shady-east-mush-field-portrayal ui-config)
        ;east-mush-field-portrayal (:east-mush-field-portrayal ui-config)
        west-snipe-field-portrayal (:west-snipe-field-portrayal ui-config)
        ;east-snipe-field-portrayal (:east-snipe-field-portrayal ui-config)
        west-display (setup-display this width height)
        west-display-frame (setup-display-frame west-display controller "west subenv" true)
        ;east-display (setup-display this width height)
        ;east-display-frame (setup-display-frame east-display controller "east subenv" true)
        ;superimposed-display (setup-display this width height)
        ;superimposed-display-frame (setup-display-frame superimposed-display controller "overlapping subenvs" false)
        ] ; false supposed to hide it, but fails
    (reset! (:west-display ui-config) west-display)
    (reset! (:west-display-frame ui-config) west-display-frame)
    ;(reset! (:east-display ui-config) east-display)
    ;(reset! (:east-display-frame ui-config) east-display-frame)
    ;(reset! (:superimposed-display ui-config) superimposed-display)
    ;(reset! (:superimposed-display ui-config) superimposed-display)
    ;(reset! (:superimposed-display-frame ui-config) superimposed-display-frame)
    (attach-portrayals! west-display [;[bg-field-portrayal "west bg"] ; two separate bg portrayals so line between subenvs will be visible
                                      ;[west-mush-field-portrayal "west mush"]
                                      [west-snipe-field-portrayal "west snip"]]
                        0 0 width height)
    ;(attach-portrayals! east-display [;[bg-field-portrayal "east bg"]
    ;                                  [east-mush-field-portrayal "east mush"]
    ;                                  [east-snipe-field-portrayal "east snipe"]]
    ;                    0 0 width height)
    ;; "superimposed" display with subenvs occupying the same space on the screen:
    ;(attach-portrayals! superimposed-display [[bg-field-portrayal "bg"]] 0 0 (+ width superimposed-subenv-offset) height)
    ;(attach-portrayals! superimposed-display [[west-mush-field-portrayal "west mush"]] 0 0 width height)
    ;(attach-portrayals! superimposed-display [[shady-east-mush-field-portrayal "east mush"]] superimposed-subenv-offset 0 width height)
    ;(attach-portrayals! superimposed-display [[west-snipe-field-portrayal "west snipe"]] 0 0 width height)
    ;(attach-portrayals! superimposed-display [[east-snipe-field-portrayal "east snipe"]] superimposed-subenv-offset 0 width height)
    ))

(defn -quit
  [this]
  (.superQuit this)
  (let [ui-config (.getUIState this)
        west-display (:west-display ui-config)
        west-display-frame (:west-display-frame ui-config)
        ;east-display (:east-display ui-config)
        ;east-display-frame (:east-display-frame ui-config)
        ;superimposed-display (:superimposed-display ui-config)
        ;superimposed-display-frame (:superimposed-display-frame ui-config)
        sim (.getState this)
        sim-data$ (.simData sim)]
    (when west-display-frame (.dispose west-display-frame))
    ;(when east-display-frame (.dispose east-display-frame))
    ;(when superimposed-display-frame (.dispose superimposed-display-frame))
    (reset! (:west-display ui-config) nil)
    (reset! (:west-display-frame ui-config) nil)
    ;(reset! (:east-display ui-config) nil)
    ;(reset! (:east-display-frame ui-config) nil)
    ;(reset! (:superimposed-display ui-config) nil)
    ;(reset! (:superimposed-display-frame ui-config) nil)
    (when-let [writer (:csv-writer @sim-data$)]
      (.close writer))))

;; Try this:
;; (let [snipes (.elements (:snipe-field (:popenv @sim-data$))) N (count snipes) energies (map :energy snipes)] [N (/ (apply + energies) N)])
(defn repl-gui
  "Convenience function to init and start GUI from the REPL.
  Returns the new Sim object.  Usage e.g.:
  (use 'example.UI) 
  (let [[sim ui] (repl-gui)] (def sim sim) (def ui ui)) ; considered bad practice--but convenient in this case
  (def data$ (.simData sim))"
  []
  (let [sim (Sim. (System/currentTimeMillis))
        ui (example.UI. sim)]
    (.setVisible (Console. ui) true)
    [sim ui]))

(defmacro repl-gui-with-defs
  "Calls repl-gui to start the gui, then creates top-level definitions:
  sim as a example.Sim (i.e. a SimState), ui as a example.UI
  (i.e. a GUIState) that references sim, and data$ as an atom containing 
  sim's SimData stru."
  []
  (let [[sim ui] (repl-gui)]
    (def sim sim)
    (def ui ui))
  (def data$ (.simData sim))
  (println "cfg is defined as a Sim (i.e. a SimState)")
  (println "ui is defined as a UI (i.e. a GUIState)")
  (println "data$ is defined as an atom containing cfg's SimData stru."))
