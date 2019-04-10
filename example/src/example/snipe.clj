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

;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; SUPPORT FUNCTIONS

(defn rand-energy
  "Generate random energy value uniformly distributed in [0, birth-threshold)."
  [rng cfg-data]
  (math/round (* (:birth-threshold cfg-data) ; round isn't essential. just makes it easier to watch individual snipes.
                 (ran/next-double rng))))

(defn get-curr-agent-slice
  "Return a function that can be the value of getObject in Properties,
  i.e. that will return the current time-slice of a particular snipe.
  The function returned will be a closure over cfg-data$."
  [cfg-data$ first-slice] ; pass cfg-data$ and not @cfg-data$ so the fn always uses the latest data.
  (fn [] ((:snipe-map (:popenv @cfg-data$)) (:id first-slice))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; DEFAGENT VERSION:
;; This illustrates the use of defagent and -->AgentClassName (which magically
;; add and initialize the circled$ field behind the scenes); here you need to
;; pass an actual function for get-curr-agent-slice, with partial and a missing
;; last argument, so that the function can be passed the first slice from
;; inside a method in the defrecord definition.

(props/defagent RSnipe [id energy subenv x y cfg-data$] 
  (partial get-curr-agent-slice cfg-data$)
  [[:energy    java.lang.Double "Energy is what snipes get from mushrooms."]
   [:x         java.lang.Integer "x coordinate in underlying grid"]
   [:y         java.lang.Integer "y coordinate in underlying grid"]])

(defn make-rand-r-snipe 
  "Create an r-snipe with random energy."
  [rng cfg-data$ subenv new-id x y] ; fields out of order for use with partial in popenv.clj
  (-->RSnipe new-id (rand-energy rng @cfg-data$) subenv x y cfg-data$))

;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; DEFAGENT-FREE VERSION:
;; This shows how to do without defagent--you have to handle the circled$
;; field on your own, add Object and toString, and use get-curr-agent-slice 
;; with both of its arguments (since already inside the method definition.):

(comment
  (defrecord RSnipe [circled$ id energy subenv x y cfg-data$]
    Propertied
    (properties [first-slice]
      (props/make-properties
        (get-curr-agent-slice cfg-data$ first-slice) ; NOTE: get-curr-agent-slice with all args
        [:circled$ java.lang.Boolean "Field that indicates whether agent is circled in GUI."]
        [:energy    java.lang.Double "Energy is what snipes get from mushrooms."]
        [:x         java.lang.Integer "x coordinate in underlying grid"]
        [:y         java.lang.Integer "y coordinate in underlying grid"]))
    Object
    (toString [this] (str "<RSnipe " id ">")))

  (defn make-rand-r-snipe 
    "Create an r-snipe with random energy."
    [rng cfg-data$ subenv new-id x y] ; fields out of order for use with partial in popenv.clj
    (->RSnipe (atom false) new-id (rand-energy rng @cfg-data$) subenv x y cfg-data$))
)



(defn clean
  "Returns a copy of the snipe with its cfg.data$ atom removed so that
  it can be displayed in a repl without creating an infinite loop (since
  cfg-data$ contains a subenv which contains a hash of all snipes)."
  [snipe]
  (dissoc snipe :cfg-data$))
