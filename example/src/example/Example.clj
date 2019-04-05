;; This software is copyright 2016, 2017, 2018, 2019 by Marshall Abrams, 
;; and is distributed under the Gnu General Public License version 3.0 
;; as specified in the the file LICENSE.

;(set! *warn-on-reflection* true)

(ns example.Example
  (:require [example.Sim :as sim]
            [clojure.math.numeric-tower :as math])
  (:import [example snipe Sim]
           [sim.engine Steppable Schedule Stoppable]
           [sim.field.grid ObjectGrid2D] ; normally doesn't belong in UI: a hack to use a field portrayal to display a background pattern
           [sim.portrayal DrawInfo2D]
           [sim.portrayal.grid HexaObjectGridPortrayal2D]; FastHexaObjectGridPortrayal2D ObjectGridPortrayal2D
           [sim.portrayal.simple CircledPortrayal2D ShapePortrayal2D]
           [sim.display Console Display2D]
           [java.awt.geom Rectangle2D$Double] ; note wierd Clojure syntax for Java static nested class
           [java.awt Color])
  (:gen-class
    :name example.Example
    :extends sim.display.GUIState
    :main true
    :exposes {state {:get getState}}  ; accessor for field in superclass that will contain my Sim after main creates instances of this class with it.
    :exposes-methods {start superStart, quit superQuit, init superInit, getInspector superGetInspector}
    :state getUIState
    :init init-instance-state))

;; display parameters:
(def display-backdrop-color (Color. 64 64 64)) ; border around subenvs
(def snipe-size 0.55)
(defn snipe-shade-fn [max-energy snipe] (int (+ 64 (* 190 (/ (:energy snipe) max-energy)))))
(defn r-snipe-color-fn [max-energy snipe] (Color. 0 0 (snipe-shade-fn max-energy snipe)))
(def org-offset 0.6) ; with simple hex portrayals to display grid, organisms off center; pass this to DrawInfo2D to correct.

(defn -init-instance-state
  [& args]
  [(vec args) {:west-display (atom nil)       ; will be replaced in init because we need to pass the UI instance to it
               :west-display-frame (atom nil) ; will be replaced in init because we need to pass the display to it
               :west-snipe-field-portrayal (HexaObjectGridPortrayal2D.)}])

(defn -getSimulationInspectedObject
  "Override methods in sim.display.GUIState so that UI can make graphs, etc."
  [this]
  (.state this))

(defn -getInspector [this]
  "This function makes the controls for the sim state in the Model tab
  (and does other things?)."
  (let [i (.superGetInspector this)]
    (.setVolatile i true)
    i))

;;;;;;;;;;;;;;;;;;;;

(declare setup-portrayals)

(defn -main
  [& args]
  (let [sim (Sim. (System/currentTimeMillis))]  ; CREATE AN INSTANCE OF my Sim
    (when @sim/commandline$ (sim/set-sim-data-from-commandline! sim sim/commandline$)) ; we can do this in -main because we have a Sim
    (swap! (.simData sim) assoc :in-gui true) ; allow functions in Sim to check whether GUI is running
    (.setVisible (Console. (example.Example. sim)) true)))  ; THIS IS WHAT CONNECTS THE GUI TO my SimState subclass Sim

(defn mein
  "Externally available wrapper for -main."
  [args]
  (apply -main args)) ; have to use apply since already in a seq

;; This is called by the pause and go buttons when starting from fully stopped.
(defn -start
  [this-ui]
  (.superStart this-ui) ; this will call start() on the sim, i.e. in our SimState object
  (setup-portrayals this-ui))

(defn make-fnl-circled-portrayal
  "Create a subclass of CircledPortrayal2D that tracks snipes by id
  rather than by pointer identity."
  [color child-portrayal]
  (proxy [CircledPortrayal2D] [child-portrayal color false]
    (draw [snipe graphics info]
      (.setCircleShowing this @(:circled$ snipe))
      (proxy-super draw snipe graphics info))))

(defn setup-portrayals
  "Set up MASON 'portrayals' of agents and background fields.  That is, associate 
  with a given entity one or moreJava classes that will determine appearances in 
  the GUI."
  [this-ui]  ; instead of 'this': avoid confusion with e.g. proxy below
       ; first get global configuration objects and such:
  (let [sim (.getState this-ui)
        ui-config (.getUIState this-ui) ; provided by MASON
        sim-data$ (.simData sim)  ; configuration data defined by masonclj.params/defparams
        sim-data @sim-data$
        rng (.random sim)         ; a MersenneTwisterFast PRNG provided by MASON
        popenv (:popenv sim-data) ; In the pasta model this is more complicated
        west (:west popenv)
        max-energy (:max-energy sim-data)
        birth-threshold (:birth-threshold sim-data)
        effective-max-energy birth-threshold ; In the pasta model this is more complicated
        west-display @(:west-display ui-config)
        ;; set up the appearance of RSnipes:
        r-snipe-portrayal (make-fnl-circled-portrayal Color/blue
                                                             (proxy [ShapePortrayal2D][ShapePortrayal2D/X_POINTS_TRIANGLE_UP
                                                                                        ShapePortrayal2D/Y_POINTS_TRIANGLE_UP
                                                                                        (* 1.1 snipe-size)]
                                                                (draw [snipe graphics info]
                                                                  (set! (.-paint this) (r-snipe-color-fn effective-max-energy snipe)) ; paint var is in superclass
                                                                  (proxy-super draw snipe graphics (DrawInfo2D. info (* 0.75 org-offset) (* 0.55 org-offset))))))
        west-snipe-field-portrayal (:west-snipe-field-portrayal ui-config)] ; appearance of the field on which snipes run around
    (.setField west-snipe-field-portrayal (:snipe-field west))
    (.setPortrayalForClass west-snipe-field-portrayal example.snipe.RSnipe r-snipe-portrayal)
    (.scheduleRepeatingImmediatelyAfter this-ui ; this stuff is going to happen on every timestep as a result:
                                        (reify Steppable 
                                          (step [this sim-state]
                                            (let [{:keys [west]} (:popenv @sim-data$)]
                                              (.setField west-snipe-field-portrayal (:snipe-field west))))))
    ;; set up display:
    (doto west-display         (.reset) (.repaint))))

;; For h ex grid, need to rescale display (based on HexaBugsWithUI.java around line 200 in Mason 19).
;; If you use a rectangular grid, you don't need this.
(defn hex-scale-height
  "Calculate visually pleasing height for a hex grid relative to normal
  rectangular height."
  [height]
  (+ 0.5 height))

(defn hex-scale-width
  "Calculate visually pleasing width for a hex grid relative to normal
  rectangular width."
  [width] 
  (* (/ 2.0 (math/sqrt 3)) 
     (+ 1 (* (- width 1)
             (/ 3.0 4.0)))))

(defn setup-display
  "Creates and configures a MASON display object and returns it."
  [ui width height]
  (let [display (Display2D. width height ui)]
    (.setClipping display false)
    display))

(defn setup-display-frame
  "Creates and configures a MASON display-frame and returns it."
  [display controller title visible?]
  (let [display-frame (.createFrame display)]
    (.registerFrame controller display-frame)
    (.setTitle display-frame title)
    (.setVisible display-frame visible?)
    display-frame))

(defn attach-portrayals!
  "Attach field-portrayals in portrayals-with-labels to display with upper left corner 
  at x y in display and with width and height.  Order of portrayals determines
  how they are layered, with earlier portrayals under later ones."
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
        west-snipe-field-portrayal (:west-snipe-field-portrayal ui-config)
        west-display (setup-display this width height)
        west-display-frame (setup-display-frame west-display controller "west subenv" true)
        ] ; false supposed to hide it, but fails
    (reset! (:west-display ui-config) west-display)
    (reset! (:west-display-frame ui-config) west-display-frame)
    (attach-portrayals! west-display [[west-snipe-field-portrayal "west snipes"]]
                        0 0 width height)))

(defn -quit
  [this]
  (.superQuit this)
  (let [ui-config (.getUIState this)
        west-display (:west-display ui-config)
        west-display-frame (:west-display-frame ui-config)
        sim (.getState this)
        sim-data$ (.simData sim)]
    (when west-display-frame (.dispose west-display-frame))
    (reset! (:west-display ui-config) nil)
    (reset! (:west-display-frame ui-config) nil)))

;; Try this:
;; (let [snipes (.elements (:snipe-field (:popenv @sim-data$))) N (count snipes) energies (map :energy snipes)] [N (/ (apply + energies) N)])
(defn repl-gui
  "Convenience function to init and start GUI from the REPL.
  Returns the new Sim object.  Usage e.g.:
  (use 'example.Example) 
  (let [[sim ui] (repl-gui)] (def sim sim) (def ui ui)) ; considered bad practice--but convenient in this case
  (def data$ (.simData sim))"
  []
  (let [sim (Sim. (System/currentTimeMillis))
        ui (example.Example. sim)]
    (.setVisible (Console. ui) true)
    [sim ui]))

(defmacro repl-gui-with-defs
  "Calls repl-gui to start the gui, then creates top-level definitions:
  sim as a example.Sim (i.e. a SimState), ui as a example.Example
  (i.e. a GUIState) that references sim, and data$ as an atom containing 
  sim's SimData stru."
  []
  (let [[sim ui] (repl-gui)]
    (def sim sim)
    (def ui ui))
  (def data$ (.simData sim))
  (println "cfg is defined as a Sim (i.e. a SimState)")
  (println "ui is defined as a Example (i.e. a GUIState)")
  (println "data$ is defined as an atom containing cfg's SimData stru."))
