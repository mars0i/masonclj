;; This software is copyright 2016, 2017, 2018, 2019 by Marshall Abrams, 
;; and is distributed under the Gnu General Public License version 3.0 
;; as specified in the the file LICENSE.

;(set! *warn-on-reflection* true)

(ns example.snipe
  (:require [clojure.math.numeric-tower :as math]
            ;[example.perception :as perc]
            [utils.random :as ran])
  (:import [sim.util Properties SimpleProperties Propertied]
           [sim.portrayal Oriented2D])
  (:gen-class                 ; so it can be aot-compiled
     :name example.snipe)) ; without :name other aot classes won't find it


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; INITIAL UTILITY DEFS

(declare make-properties make-k-snipe make-r-snipe is-k-snipe? is-r-snipe? rand-energy atom?)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; DEFRECORD CLASS DEFS

;; The two atom fields at the end are there solely for interactions with the UI.
;; Propertied/properties is used by GUI to allow inspectors to follow a fnlly updated agent.

(defrecord RSnipe [id mush-pref energy subenv x y circled$ cfg-data$] ; r-snipe that prefers small mushrooms
  Propertied
  (properties [original-snipe] (make-properties id cfg-data$))
  Object
  (toString [this] (str "<RSnipe #" id ">")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; SNIPE MAKER FUNCTIONS

(defn make-r-snipe
  [rng cfg-data$ energy subenv new-id x y]
  (let [extreme-pref (:extreme-pref @cfg-data$)]
      (RSnipe. new-id extreme-pref energy subenv x y (atom false) cfg-data$)))

(defn make-newborn-r-snipe
  [rng cfg-data$ subenv new-id x y]
  (let [{:keys [initial-energy]} @cfg-data$]
    (make-r-snipe rng cfg-data$ initial-energy subenv new-id x y)))

(defn make-rand-r-snipe 
  "Create r-snipe with random energy (from rand-energy)."
  [rng cfg-data$ subenv new-id x y]
  (make-r-snipe rng cfg-data$ (rand-energy rng @cfg-data$) subenv new-id x y))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; MAKE-PROPERTIES FUNCTION

;; Used by GUI to allow inspectors to follow a functionally updated agent.
;; i.e. normally you can double-click on an agent, and its internal state will
;; be displayed while a circle follows it around.  The normal implementation of
;; this in MASON assumes that the JVM identity of an agent never changes; you
;; can simply track it using a pointer.  Since we are defining agents using defrecord,
;; we get all of the conveniences that Clojure provides for defrecords, when you update
;; a defrecord, you create a new objet with a new identity, and MASON can't track
;; that across updates unless you give it a special class that it will use to track
;; and agent over time.  This is what this function does.
;; (The code below makes use of the fact that in Clojure, vectors can be treated as 
;; functions ;; of indexes, returning the indexed item; that keywords such as :x can be
;; treated as functions of maps; and that defrecords such as snipes can be treated as maps.)
(defn make-properties
  "Return a Properties subclass for use by Propertied's properties method so
  that certain fields can be displayed in the GUI on request."
  [id cfg-data$]
  ;; These definitions need to be coordinated by hand:
  (let [kys [:energy :mush-pref :subenv :x :y :circled$] ; TODO CHANGE FOR NEW FIELDS
        circled-idx 5 ; HARDCODED INDEX for circled$ field              ; TODO CHANGE FOR NEW FIELDS
        descriptions ["Energy is what snipes get from mushrooms."       ; TODO CHANGE FOR NEW FIELDS
                      "Preference for large (positive number) or small (negative number) mushrooms."
                      "Name of snipe's subenv"
                      "x coordinate in underlying grid"
                      "y coordinate in underlying grid"
                      "Boolean indicating whether circled in GUI"]
        types [java.lang.Double java.lang.Double java.lang.String java.lang.Integer java.lang.Integer ; TODO CHANGE FOR NEW FIELDS
               java.lang.Boolean]                                                                     ; TODO CHANGE FOR NEW FIELDS
        read-write [false false false false false true] ; allow user to turn off circled in UI ; TODO CHANGE FOR NEW FIELDS
        names (mapv name kys)
        num-properties (count kys)
        hidden     (vec (repeat num-properties false)) ; no properties specified here are to be hidden from GUI
        get-curr-snipe (fn [] ((:snipe-map (:popenv @cfg-data$)) id))] ; find current version of this snipe
    (reset! (:circled$ (get-curr-snipe)) true) ; make-properties is only called by inspector, in which case highlight snipe in UI
    ;; Aside from the last line, the code below can probably be used as-is for any agent:
    (proxy [Properties] []
      (getObject [] (get-curr-snipe))
      (getName [i] (names i))
      (getDescription [i] (descriptions i))
      (getType [i] (types i))
      (getValue [i]
        (let [v ((kys i) (get-curr-snipe))]
          (cond (atom? v) @v
                (keyword? v) (name v)
                :else v)))
      (setValue [i newval]                  ; allow user to turn off circled in UI  ; POSS CHANGE FOR NEW FIELDS
        (when (= i circled-idx)             ; returns nil/null for other fields
          (reset! (:circled$ (get-curr-snipe))
                  (Boolean/valueOf newval)))) ; it's always a string that's returned from UI. (Do *not* use (Boolean. newval); it's always truthy in Clojure.)
      (isHidden [i] (hidden i))
      (isReadWrite [i] (read-write i))
      (isVolatile [] false)
      (numProperties [] num-properties)
      (toString [] (str "<SimpleProperties: snipe id=" id ">")))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; MISCELLANEOUS LITTLE FUNCTIONS

(defn atom? [x] (instance? clojure.lang.Atom x)) ; This is unlikely to become part of clojure.core: http://dev.clojure.org/jira/browse/CLJ-1298

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
