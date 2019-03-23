;; This software is copyright 2016, 2017, 2018, 2019 by Marshall Abrams, 
;; and is distributed under the Gnu General Public License version 3.0 
;; as specified in the the file LICENSE.

;(set! *warn-on-reflection* true)

(ns example.Sim
  (:require [clojure.tools.cli]
            [clojure.data.csv :as csv]
            [clojure.java.io]
            [masonclj.simparams :as sp]
            ;[utils.map2csv :as m2c]
            [example.snipe :as sn]
            [example.popenv :as pe]
            ;[example.stats :as stats]
            )
  (:import [sim.engine Steppable Schedule Stoppable]
           [sim.util Interval]
           [ec.util MersenneTwisterFast]
           [java.lang String]
           ;[java.io BufferedWriter]
           [example.popenv PopEnv]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Generate Sim class as subclass of SimState using genclass, with an init 
;; function, import statement, and Bean/MASON field accessors.
;; To see what code will be generated, try this in a repl:
;;    (require '[utils.defsim :as defsim])
;;    (pprint (macroexpand-1 '<insert defsim call>))

(def commandline$ (atom nil)) ; Used by record-commandline-args!, which is defined by defsim, and below

;; See https://stackoverflow.com/questions/8435681/how-to-convert-a-clojure-string-of-numbers-into-separate-integers
;; and https://stackoverflow.com/questions/2640169/whats-the-easiest-way-to-parse-numbers-in-clojure:
(defn string-to-map
  "Read a string containing comma-separated integers into a Clojure map."
  [s]
  (clojure.edn/read-string (str "{" s "}")))

;; Rather than using the constructors in in-line functions,
;; define converters here with type hints to avoid reflection warnings:
;(defn string-to-Long [^String s] (Long. s)) (defn string-to-Double [^String s] (Double. s)) (defn string-to-Double [^String s] (Double. s))
;; The next one is included only for parallelism.  You can't type hint the argument
;; in this case, because it's generally a nothing rather than a string.
;; i.e. clojure.tools.cli handles boolean options specially.  If you
;; type hint it, it will always behave as if it was false.  So you will
;; always get a reflection warning for the parameter s.  But that's OK
;; since this runs only once when you start the application.
;(defn string-to-Boolean [s] (Boolean. s))

;; Note: There is no option below for max number of steps.  Use MASON's -for instead.
;; Avoid the following characters for single-character options, because MASON already
;; uses them for single-dash options: c d f h p q r s t u.  Also avoid numbers, because
;; MASON allows setting '-seed <old seed number>', and old seed number may be a negative
;; number, in which case the app gets confused if I use e.g. -2 as an option below.
(sp/defparams  [;field name   initial-value type             in ui? with range?
                [num-r-snipes       25      long                    [0,500]     ["-R" "Size of r-snipe subpopulation" :parse-fn #(Long. %)]]
                [initial-energy     10.0    double                  [0.0,50.0]  ["-e" "Initial energy for each snipe" :parse-fn #(Double. %)]]
                [birth-threshold    20.0    double                  [1.0,50.0]  ["-b" "Energy level at which birth takes place" :parse-fn #(Double. %)]]
                [birth-cost          5.0    double                  [0.0,10.0]  ["-o" "Energetic cost of giving birth to one offspring" :parse-fn #(Double. %)]]
                [max-energy         30.0    double                  [1.0,100.0] ["-E" "Max energy that a snipe can have." :parse-fn #(Double. %)]]
                [lifespan-mean       0      long                    [0,500]     ["-L" "Each snipe dies after a normally distributed number of timesteps with this mean." :parse-fn #(Long. %)]]
                [lifespan-sd         0      long                    [0,10]      ["-l" "Each snipe dies after a normally distributed number of timesteps with this standard deviation." :parse-fn #(Long. %)]]
                [carrying-proportion 0.25   double                  [0.1,0.9]   ["-C" "Snipes are randomly culled when number exceed this times # of cells in a subenv (east or west)." :parse-fn #(Double. %)]]
                [neighbor-radius     5      long                    [1,10]      ["-D" "s-snipe neighbors (for copying) are no more than this distance away." :parse-fn #(Long. %)]]
                [env-width          40      long                    [10,250]    ["-W" "Width of env.  Must be an even number." :parse-fn #(Long. %)]] ; Haven't figured out how to change 
                [env-height         40      long                    [10,250]    ["-H" "Height of env. Must be an even number." :parse-fn #(Long. %)]] ;  within app without distortion
                [env-display-size   12.0    double                  false       ["-G" "How large to display the env in gui by default." :parse-fn #(Double. %)]]
                [use-gui           false    boolean                 false       ["-g" "If -g, use GUI; otherwise use GUI if and only if +g or there are no commandline options." :parse-fn #(Boolean. %)]]
                [extreme-pref        1.0    double                  true        ["-x" "Absolute value of r-snipe preferences." :parse-fn #(Double. %)]]
                ;[report-every        0      double                  true        ["-i" "Report basic stats every i ticks after the first one (0 = never); format depends on -w." :parse-fn #(Double. %)]]
                [r-max-pop-sizes    nil clojure.lang.IPersistentMap true        ["-U" "Comma-separated times and target subpop sizes to cull r-snipes to, e.g. time,size,time,size" :parse-fn string-to-map]]
                [r-min-pop-sizes    nil clojure.lang.IPersistentMap true        ["-Y" "Comma-separated times and target subpop sizes to increase r-snipes to, e.g. time,size,time,size" :parse-fn string-to-map]]
                [csv-writer         nil java.io.BufferedWriter false]
                [max-subenv-pop-size 0      long    false] ; maximum per-subenvironment population size
                [seed               nil     long    false] ; convenience field to store Sim's seed
                [in-gui           false     boolean false] ; convenience field to store Boolean re whether in GUI
                [popenv             nil  example.popenv.PopEnv false]
               ]
  :exposes-methods {finish superFinish} ; name for function to call finish() in the superclass
  :methods [[getPopSize [] long]] ; Signatures for Java-visible methods that won't be autogenerated, but be defined below.
  )

;; no good reason to put this into the defsim macro since it doesn't include any
;; field-specific code.  Easier to redefine if left here.
(defn set-sim-data-from-commandline!
  "Set fields in the Sim's simData from parameters passed on the command line."
  [^Sim sim cmdline$]
  (let [options (:options @cmdline$)
        sim-data (.simData sim)]
    (run! #(apply swap! sim-data assoc %) ; arg is a MapEntry, which is sequential? so will function like a list or vector
          options)))

;; NOTE called on every tick by GUI even if Model window is not displayed:
(defn -getPopSize
  [^Sim this] 
  (count (:snipe-map (:popenv @(.simData this)))))

(defn curr-step [^Sim sim] (.getSteps (.schedule sim)))
(defn curr-popenv [^Sim sim] (:popenv @(.simData sim)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn -main
  [& args]
  (let [^"[Ljava.lang.String;" arg-array (into-array args)]
    (sim.engine.SimState/doLoop example.Sim arg-array)
    (System/exit 0)))

(defn mein
  "Externally available wrapper for -main."
  [args]
  (apply -main args)) ; have to use apply since already in a seq

;; I'm not sure whether this is ever called by MASON.
(defn -stop
  [^Sim this]
  (let [^SimData sim-data$ (.simData this)]
    ;; You could do something here.
    ))

(defn cleanup
  [^Sim this]
  (let [^SimData sim-data$ (.simData this)
        ^Stoppable stoppable (:stoppable @sim-data$)]
    (.stop stoppable)))

;; This should not call the corresponding function in the superclass; that
;; function will call this one.  So if you want to call this function
;; explicitly, you may want to do so by calling superFinish, which 
;; can be defined in the defsim statement above using :exposes-methods.
;; However, if you always use MASON capabilities to end simulations (e.g.
;; using -for or -until on the command line), you don't need to call
;; superFinish, and this function here will automatically get called.
;; (I think line 662 of MASON 19's SimState.java might be where this happens.)
(defn -finish
  [^Sim this]
  (cleanup this))

;; Note finish is never called here.  Stopping a simulation in any
;; normal MASON way will result in finish() above being called.
(defn run-sim
  [^Sim sim-sim rng sim-data$ seed]
  (let [^Schedule schedule (.schedule sim-sim)
        ;report-every (:report-every @sim-data$)
        max-ticks (:max-ticks @sim-data$)
        ;; This runs the simulation:
        ^Stoppable stoppable (.scheduleRepeating schedule Schedule/EPOCH 0 ; epoch = starting at beginning, 0 means run this first during timestep
                                      (reify Steppable 
                                        (step [this sim-state]
                                          (swap! sim-data$ assoc :curr-step (curr-step sim-sim)) ; make current tick available to popenv
                                          (swap! sim-data$ update :popenv pe/next-popenv rng sim-data$))))]
    (swap! sim-data$ assoc :stoppable stoppable))) ; store this to make available to finish()

(defn -start
  "Function that's called to (re)start a new simulation run."
  [^Sim this]
  (.superStart this)
  ;; Construct core data structures of the simulation:
  (let [^SimData sim-data$ (.simData this)
        ^MersenneTwisterFast rng (.-random this)
        seed (.seed this)]
    ;; If user passed commandline options, use them to set parameters, rather than defaults:
    (when (and @commandline$ (not (:in-gui @sim-data$))) ; see issue #56 in github for the logic here
      (set-sim-data-from-commandline! this commandline$))
    (swap! sim-data$ assoc :seed seed)
    (pe/setup-popenv-config! sim-data$)
    (swap! sim-data$ assoc :popenv (pe/make-popenv rng sim-data$)) ; create new popenv
    ;; Run it:
    (run-sim this rng sim-data$ seed)))
