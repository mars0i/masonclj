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


(declare rand-energy)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; DEFRECORD CLASS DEFS

(defn make-get-curr-obj
  "Return a function that can be the value of getObject in Properties,
  i.e. that will return the current time-slice of a particular snipe.
  The function returned will be a closure over cfg-data$."
  [cfg-data$ first-slice] ; pass cfg-data$ and not @cfg-data$ so the fn always uses the latest data.
  (fn [] ((:snipe-map (:popenv @cfg-data$)) (:id first-slice))))

;; DIFFERENT FROM DEFAGENT VERSION:
(defrecord RSnipe [circled$ id energy subenv x y cfg-data$]
  Propertied
  (properties [first-slice]
    (props/make-properties
      (make-get-curr-obj cfg-data$ first-slice)
      [:circled$ java.lang.Boolean "Field that indicates whether agent is circled in GUI."]
      [:energy    java.lang.Double "Energy is what snipes get from mushrooms."]
      [:x         java.lang.Integer "x coordinate in underlying grid"]
      [:y         java.lang.Integer "y coordinate in underlying grid"]))
  Object
  (toString [this] (str "<RSnipe " id ">")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; SNIPE MAKER FUNCTIONS

;; DIFFERENT FROM DEFAGENT VERSION:
(defn make-r-snipe
  [cfg-data$ energy subenv new-id x y]
  (->RSnipe (atom false) new-id energy subenv x y cfg-data$))

(defn make-rand-r-snipe 
  "Create r-snipe with random energy (from rand-energy)."
  [rng cfg-data$ subenv new-id x y]
  (make-r-snipe cfg-data$ (rand-energy rng @cfg-data$) subenv new-id x y))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; MISCELLANEOUS LITTLE FUNCTIONS

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
