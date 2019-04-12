;; This software is copyright 2016, 2017, 2018, 2019 by Marshall Abrams, 
;; and is distributed under the Gnu General Public License version 3.0 
;; as specified in the the file LICENSE.

;(set! *warn-on-reflection* true)

(ns example.GUI
  (:require [example.Sim :as sim]
            [masonclj.properties :as props]
            [clojure.math.numeric-tower :as math])
  (:import [example snipe Sim]
           [sim.engine Steppable Schedule Stoppable]
           [sim.field.grid ObjectGrid2D] ; normally doesn't belong in GUI: a hack to use a field portrayal to display a background pattern
           [sim.portrayal DrawInfo2D]
           [sim.portrayal.grid HexaObjectGridPortrayal2D]; FastHexaObjectGridPortrayal2D ObjectGridPortrayal2D
           [sim.portrayal.simple CircledPortrayal2D ShapePortrayal2D]
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

;(defn -getName
;  "This doesn't work."
;  ([this] (println "void version") "Yow!")
;  ([this cls] (println "sending up to super") (.superGetName this cls)))

;; display parameters:
(def display-backdrop-color (Color. 64 64 64)) ; border around subenvs
(def snipe-size 0.55)
(defn snipe-shade-fn [max-energy snipe] (int (+ 64 (* 190 (/ (:energy snipe) max-energy)))))
(defn r-snipe-color-fn [max-energy snipe] (Color. 0 0 (snipe-shade-fn max-energy snipe)))
(def org-offset 0.6) ; with simple hex portrayals to display grid, organisms off center; pass this to DrawInfo2D to correct.

(defn -init-instance-state
  [& args]
  [(vec args) {:west-display (atom nil)       ; will be replaced in init because we need to pass the GUI instance to it
               :west-display-frame (atom nil) ; will be replaced in init because we need to pass the display to it
               :west-snipe-field-portrayal (HexaObjectGridPortrayal2D.)}])

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

;;;;;;;;;;;;;;;;;;;;

(declare setup-portrayals)

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

;; This is called by the pause and go buttons when starting from fully stopped.
(defn -start
  [this-gui]
  (.superStart this-gui) ; this will call start() on the sim, i.e. in our SimState object
  (setup-portrayals this-gui))

;; IN the Example model, THERE IS NO east- anything.  It's all west-.
(defn setup-portrayals
  "Set up MASON 'portrayals' of agents and background fields.  That is, associate 
  with a given entity one or moreJava classes that will determine appearances in 
  the GUI."
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
        west-display @(:west-display gui-config)
        ;; Set up the appearance of RSnipes with a main portrayal inside one 
        ;; that can display a circle around it:
        r-snipe-portrayal (props/make-fnl-circled-portrayal Color/blue
                            (proxy [ShapePortrayal2D][ShapePortrayal2D/X_POINTS_TRIANGLE_UP ; there's a simpler way but
                                                      ShapePortrayal2D/Y_POINTS_TRIANGLE_UP ; this one is more flexible
                                                      (* 1.1 snipe-size)]
                                   (draw [snipe graphics info]
                                     (set! (.-paint this) (r-snipe-color-fn max-energy snipe)) ; paint var is in superclass
                                     (proxy-super draw snipe graphics (DrawInfo2D. info (* 0.75 org-offset) (* 0.55 org-offset))))))
        west-snipe-field-portrayal (:west-snipe-field-portrayal gui-config)] ; appearance of the field on which snipes run around
    (.setField west-snipe-field-portrayal (:snipe-field west))
    (.setPortrayalForClass west-snipe-field-portrayal example.snipe.RSnipe r-snipe-portrayal)
    (.scheduleRepeatingImmediatelyAfter this-gui ; this stuff is going to happen on every timestep as a result:
                                        (reify Steppable 
                                          (step [this sim-state]
                                            (let [{:keys [west]} (:popenv @sim-data$)]
                                              (.setField west-snipe-field-portrayal (:snipe-field west))))))
    ;; set up display:
    (doto west-display         (.reset) (.repaint))))

;; For hex grid, need to rescale display (based on HexaBugsWithUI.java around line 200 in Mason 19).
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
  [gui width height]
  (let [display (Display2D. width height gui)]
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
        gui-config (.getUIState this)
        sim-data @(.simData sim) ; just for env dimensions
        display-size (:env-display-size sim-data)
        width  (hex-scale-width  (int (* display-size (:env-width sim-data))))
        height (hex-scale-height (int (* display-size (:env-height sim-data))))
        west-snipe-field-portrayal (:west-snipe-field-portrayal gui-config)
        west-display (setup-display this width height)
        west-display-frame (setup-display-frame west-display controller "west subenv" true)
        ] ; false supposed to hide it, but fails
    (reset! (:west-display gui-config) west-display)
    (reset! (:west-display-frame gui-config) west-display-frame)
    (attach-portrayals! west-display [[west-snipe-field-portrayal "west snipes"]]
                        0 0 width height)))

(defn -quit
  [this]
  (.superQuit this)
  (let [gui-config (.getUIState this)
        west-display (:west-display gui-config)
        west-display-frame (:west-display-frame gui-config)
        sim (.getState this)
        sim-data$ (.simData sim)]
    (when west-display-frame (.dispose west-display-frame))
    (reset! (:west-display gui-config) nil)
    (reset! (:west-display-frame gui-config) nil)))

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
  "Calls repl-gui to start the gui, then creates top-level definitions:
  sim as a example.Sim (i.e. a SimState), gui as a example.GUI
  (i.e. a GUIState) that references sim, and data$ as an atom containing 
  sim's SimData stru."
  []
  (let [[sim gui] (repl-gui)]
    (def sim sim)
    (def gui gui))
  (def data$ (.simData sim))
  (println "cfg is defined as a Sim (i.e. a SimState)")
  (println "gui is defined as a GUI (i.e. a GUIState)")
  (println "data$ is defined as an atom containing cfg's SimData stru."))
