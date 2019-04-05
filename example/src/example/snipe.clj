;; This software is copyright 2016, 2017, 2018, 2019 by Marshall Abrams, 
;; and is distributed under the Gnu General Public License version 3.0 
;; as specified in the the file LICENSE.

;(set! *warn-on-reflection* true)

(ns example.snipe
  (:require [clojure.math.numeric-tower :as math]
            [masonclj.properties :as props]
            [utils.random :as ran])
  (:import [sim.util Properties SimpleProperties Propertied]
           [sim.portrayal Oriented2D])
  (:gen-class                 ; so it can be aot-compiled
     :name example.snipe)) ; without :name other aot classes won't find it


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; INITIAL UTILITY DEFS

(declare make-properties make-k-snipe make-r-snipe is-k-snipe? is-r-snipe? rand-energy)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; DEFRECORD CLASS DEFS

(defn make-get-curr-obj
  "Return a function that can be the value of getObject in Properties,
  i.e. that will return the current time-slice of a particular snipe.
  The function returned will be a closure over cfg-data$."
  [cfg-data$ original-snipe] ; pass cfg-data$ and not @cfg-data$ so the fn always uses the latest data.
  (fn [] ((:snipe-map (:popenv @cfg-data$)) (:id original-snipe))))

(props/defagt RSnipe [id energy subenv x y cfg-data$] 
  (partial make-get-curr-obj cfg-data$)
  [[:energy    java.lang.Double "Energy is what snipes get from mushrooms."]
   [:x         java.lang.Integer "x coordinate in underlying grid"]
   [:y         java.lang.Integer "y coordinate in underlying grid"]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; SNIPE MAKER FUNCTIONS

(defn make-r-snipe
  [cfg-data$ energy subenv new-id x y]
  (-->RSnipe new-id energy subenv x y cfg-data$)) ; NOTE circled$ is placed first by defagt

(defn make-rand-r-snipe 
  "Create r-snipe with random energy (from rand-energy)."
  [rng cfg-data$ subenv new-id x y]
  (make-r-snipe cfg-data$ (rand-energy rng @cfg-data$) subenv new-id x y))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; MAKE-PROPERTIES FUNCTION

;; Used by GUI to allow inspectors to follow a functionally updated agent.
;; i.e. normally you can double-click on an agent, and its internal state will
;; be displayed (while a circle follows it around, if you implement that in UI code).
;; The normal implementation of this in MASON assumes that the JVM identity of an 
;; agent never changes; you can simply track it using a pointer.  Since we are defining 
;; agents using defrecord, we get all of the conveniences that Clojure provides for 
;; defrecords, when you update a defrecord, you create a new objet with a new identity,
;; and MASON can't track that across updates unless you give it a special class that it 
;; will use to track and agent over time.  This is what this function does.
;; (The code below makes use of the fact that in Clojure, vectors can be treated as 
;; functions ;; of indexes, returning the indexed item; that keywords such as :x can be
;; treated as functions of maps; and that defrecords such as snipes can be treated as maps.)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; MISCELLANEOUS LITTLE FUNCTIONS

;; SHOULD THIS BE GAUSSIAN?
;; Is birth-threshold the right limit?
(defn rand-energy
  "Generate random energy value uniformly distributed in [0, birth-threshold)."
  [rng cfg-data]
  (math/round (* (:birth-threshold cfg-data) ; round isn't essential. just makes it easier to watch individual snipes.
                 (ran/next-double rng))))

(defn clean
  "Returns a copy of the snipe with its cfg.data$ atom removed so that
  it can be displayed in a repl without creating an infinite loop (since
  cfg-data$ contains a subenv which contains a hash of all snipes)."
  [snipe]
  (dissoc snipe :cfg-data$))

(defn r-snipe? [s] (instance? example.snipe.RSnipe s))
