;; This software is copyright 2016, 2017, 2018, 2019 by Marshall Abrams, 
;; and is distributed under the Gnu General Public License version 3.0 
;; as specified in the the file LICENSE.

;(set! *warn-on-reflection* true)

(ns example.GUI
  (:require [example.Sim :as sim]
            [masonclj.properties :as props]
            [clojure.math.numeric-tower :as math])
  (:import [example snipe Sim]
           [sim.engine Steppable]
           [sim.field.grid ObjectGrid2D]
           [sim.portrayal DrawInfo2D]
           [sim.portrayal.grid HexaObjectGridPortrayal2D]
           [sim.portrayal.simple CircledPortrayal2D OvalPortrayal2D HexagonalPortrayal2D]
           [sim.display Console Display2D]
           [java.awt.geom Rectangle2D$Double] ; note wierd Clojure syntax for Java static nested class
           [java.awt Color])
  (:gen-class
    :name example.GUI
    :extends sim.display.GUIState
    :main true
    :methods [^:static [getName [] java.lang.String]] ; see comment on the implementation below
    :exposes {state {:get getState}}  ; accessor for field in superclass that will contain my Sim after main creates instances of this class with it.
    :exposes-methods {start superStart,
                      quit superQuit,
                      init superInit,
                      getInspector superGetInspector,
                      getName superGetName}
    :state getUIState
    :init init-instance-state))

(declare setup-portrayals setup-display! setup-display-frame! attach-portrayals!)

;; getName() is static in GUIState.  You can't actually override a static
;; method, normally, in the sense that the method to run would be chosen
;; at runtime by the actual class used.  Rather, with a static method,
;; the declared class of a variable determines at compile time which 
;; method to call.  *But* MASON uses reflection to figure out which
;; method to call at runtime.  Nevertheless, this means that we need 
;; a declaration in :methods, which you would not do for overriding
;; non-static methods from a superclass.  Also, the declaration should 
;; be declared static using metadata *on the entire declaration vector.
(defn -getName 
  "`\"Overrides\" the no-arg static getName() method in GUIState, and
  returns the name to be displayed on the title bar of the main window."
  []
  "masonclj example")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; CONFIGURATION AND UTILITY FUNCTIONS

;; display parameters:
(def display-backdrop-color (Color. 200 200 200)) ; border color around hex cells and overall field
(def snipe-size 0.70)
(defn snipe-shade-fn [max-energy snipe] (int (+ 64 (* 190 (/ (:energy snipe) max-energy)))))
(defn snipe-color-fn [max-energy snipe] (Color. 0 0 (snipe-shade-fn max-energy snipe)))
(def org-offset 0.6) ; with simple hex portrayals to display grid, organisms off center; pass this to DrawInfo2D to correct.

;; For hex grid, need to rescale display (based on the MASON example 
;; HexaBugsWithUI.java around line 200 in Mason 19).  If you use a
;; rectangular grid, you don't need this.
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

(defn -getSimulationInspectedObject
  "Override methods in sim.display.GUIState so that GUI can make graphs, etc."
  [this]
  (.state this))

(defn -getInspector [this]
  "This function makes the controls for the sim state in the Model tab
  (and does other things?)."
  (let [i (.superGetInspector this)]
    (.setVolatile i true)
    i))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; gen-class instance variable initialization function

(defn -init-instance-state
  [& args]
  [(vec args) {:display (atom nil)       ; will be replaced in init because we need to pass the GUI instance to it
               :display-frame (atom nil) ; will be replaced in init because we need to pass the display to it
               :snipe-field-portrayal (HexaObjectGridPortrayal2D.)      ; hex grid on which snipes move (required)
               :hexagonal-bg-field-portrayal (HexaObjectGridPortrayal2D.)}]) ; hex grid for background pattern (not required)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; MAIN GUI SETUP ROUTINES

(defn -main
  [& args]
  (let [sim (Sim. (System/currentTimeMillis))]  ; CREATE AN INSTANCE OF my Sim
    (when @sim/commandline$ (sim/set-sim-data-from-commandline! sim sim/commandline$)) ; we can do this in -main because we have a Sim
    (swap! (.simData sim) assoc :in-gui true) ; allow functions in Sim to check whether GUI is running
    (.setVisible (Console. (example.GUI. sim)) true)))  ; THIS IS WHAT CONNECTS THE GUI TO my SimState subclass Sim

(defn mein
  "Externally available wrapper for -main."
  [args]
  (apply -main args)) ; have to use apply since already in a seq

(defn -init
  "Initialization function caused to be run by MASON SimState class."
  [this controller] ; fyi controller is called c in Java version
  (.superInit this controller)
  (let [sim (.getState this)
        gui-config (.getUIState this)
        sim-data @(.simData sim) ; just for env dimensions
        display-size (:env-display-size sim-data)
        width  (hex-scale-width  (int (* display-size (:env-width sim-data))))
        height (hex-scale-height (int (* display-size (:env-height sim-data))))
        snipe-field-portrayal (:snipe-field-portrayal gui-config)
        hexagonal-bg-field-portrayal (:hexagonal-bg-field-portrayal gui-config)
        display (setup-display! this width height)
        display-frame (setup-display-frame! display controller "Example" true)] ; false supposed to hide it, but fails
    (reset! (:display gui-config) display)
    (reset! (:display-frame gui-config) display-frame)
    ;; Attach layers to display; later layers will be on top, and can hide earlier ones:
    (attach-portrayals! display [[hexagonal-bg-field-portrayal "west bg"]    ; background pattern, not required
                                      [snipe-field-portrayal "west snipes"]] ; snipes have to be on top
                        0 0 width height)))

(defn -start
  "Function run by pause and go buttons when starting from fully stopped state."
  [this-gui]
  (.superStart this-gui) ; this will call start() on the sim, i.e. in our SimState object
  (setup-portrayals this-gui))

(defn attach-portrayals!
  "Attach field-portrayals in portrayals-with-labels to display with upper left corner 
  at x y in display and with width and height.  Order of portrayals determines
  how they are layered, with earlier portrayals under later ones."
  [display portrayals-with-labels x y field-width field-height]
  (doseq [[portrayal label] portrayals-with-labels]
    (.attach display portrayal label
             (Rectangle2D$Double. x y field-width field-height)))) ; note Clojure $ syntax for Java static nested classes

;; IN the Example model, THERE IS NO east- anything.  It's all west-.
(defn setup-portrayals
  "Set up MASON 'portrayals' of agents and background fields.  That is, associate 
  with a given entity one or more Java classes that will determine appearances in 
  the GUI.  Usually called from start."
  [this-gui]  ; instead of 'this': avoid confusion with e.g. proxy below
  ; first get global configuration objects and such:
  (let [sim (.getState this-gui)
        gui-config (.getUIState this-gui) ; provided by MASON
        sim-data$ (.simData sim)  ; configuration data defined by masonclj.params/defparams
        sim-data @sim-data$
        rng (.random sim)         ; a MersenneTwisterFast PRNG provided by MASON
        popenv (:popenv sim-data) ; In the pasta model this is more complicated
        west (:west popenv)
        max-energy (:max-energy sim-data)
        display @(:display gui-config)
        hexagonal-bg-field-portrayal (:hexagonal-bg-field-portrayal gui-config)
        ;; Set up the appearance of Snipes with a main portrayal inside one 
        ;; that can display a circle around it:
        snipe-portrayal (props/make-fnl-circled-portrayal Color/blue
                            (proxy [OvalPortrayal2D][snipe-size]
                                   (draw [snipe graphics info]
                                     (set! (.-paint this) (snipe-color-fn max-energy snipe)) ; paint var is in superclass
                                     (proxy-super draw snipe graphics (DrawInfo2D. info (* 0.75 org-offset) (* 0.55 org-offset)))))) ; center in cell
        snipe-field-portrayal (:snipe-field-portrayal gui-config)] ; appearance of the field on which snipes run around
    (.setField hexagonal-bg-field-portrayal (ObjectGrid2D. (:env-width sim-data) (:env-height sim-data))) ; dimensions of background grid
    (.setField snipe-field-portrayal (:snipe-field west))
    (.setPortrayalForNull hexagonal-bg-field-portrayal (HexagonalPortrayal2D. (Color. 255 255 255) 0.90)) ; show patches at smaller size so borders show
    (.setPortrayalForClass snipe-field-portrayal example.snipe.Snipe snipe-portrayal)
    (.scheduleRepeatingImmediatelyAfter this-gui ; this stuff is going to happen on every timestep as a result:
                                        (reify Steppable 
                                          (step [this sim-state]
                                            (let [{:keys [west]} (:popenv @sim-data$)]
                                              (.setField snipe-field-portrayal (:snipe-field west))))))
    ;; set up display:
    (doto display
          (.reset)
          (.setBackdrop display-backdrop-color) ; bottom level color--will show through around hexagaons
          (.repaint))))

(defn setup-display!
  "Creates and configures a MASON display object and returns it.  Usually
  called from init."
  [gui width height]
  (let [display (Display2D. width height gui)]
    (.setClipping display false)
    display))

(defn setup-display-frame!
  "Creates and configures a MASON display-frame and returns it.  Usually
  called from init."
  [display controller title visible?]
  (let [display-frame (.createFrame display)]
    (.registerFrame controller display-frame)
    (.setTitle display-frame title)
    (.setVisible display-frame visible?)
    display-frame))

(defn -quit
  "Cleans up state before exiting model."
  [this]
  (.superQuit this)
  (let [gui-config (.getUIState this)
        display (:display gui-config)
        display-frame (:display-frame gui-config)
        sim (.getState this)
        sim-data$ (.simData sim)]
    (when display-frame (.dispose display-frame))
    (reset! (:display gui-config) nil)
    (reset! (:display-frame gui-config) nil)))

;; Try this:
;; (let [snipes (.elements (:snipe-field (:popenv @sim-data$))) N (count snipes) energies (map :energy snipes)] [N (/ (apply + energies) N)])
(defn repl-gui
  "Convenience function to init and start GUI from the REPL.
  Returns the new Sim object.  Usage e.g.:
  (use 'example.GUI) 
  (let [[sim gui] (repl-gui)] (def sim sim) (def gui gui)) ; considered bad practice--but convenient in this case
  (def data$ (.simData sim))"
  []
  (let [sim (Sim. (System/currentTimeMillis))
        gui (example.GUI. sim)]
    (.setVisible (Console. gui) true)
    [sim gui]))

(defmacro repl-gui-with-defs
  "Calls repl-gui to start the GUI, then creates top-level definitions:
  sim as an example.Sim (i.e. a SimState), gui as an example.GUI
  (i.e. a GUIState) that references sim, and data$ as an atom containing 
  sim's SimData stru."
  []
  (let [[sim gui] (repl-gui)]
    (def sim sim)
    (def gui gui))
  (def data$ (.simData sim))
  (println "sim is defined as a Sim (i.e. a SimState)")
  (println "gui is defined as a GUI (i.e. a GUIState)")
  (println "data$ is defined as an atom containing cfg's SimData stru."))
