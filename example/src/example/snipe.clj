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

;; The real difference between snipe types is in perception/preferences occurs,
;; so you don't really need separate defrecords--except that it's easier to
;; display snipes of different types differently in the UI if they're represented
;; by different Java classes.

;; Notes on Orientation2D, etc.:
;; value   orientation
;;   0       3:00
;;  pi/2     6:00
;;   pi      9:00
;;  -pi      9:00
;; 1.5*pi   12:00
;; -pi/2    12:00
;; FIXME THIS IS *REALLY* SLOW
(defn pref-orientation
  [minimum maximum value]
  (let [size (- maximum minimum) ; can I move this out so it's not recalc'ed every time?
        normalized-value (- (/ (- value minimum) size) ; scale value so it's in [-0.5 0.5]
                            0.5)
        orientation (* -1 normalized-value Math/PI)]
    (min (* 0.5 Math/PI)
         (max (* -0.5 Math/PI) 
              orientation)))) ; even given normalization some schemes might produce values outside the range

;; The two atom fields at the end are there solely for interactions with the UI.
;; Propertied/properties is used by GUI to allow inspectors to follow a fnlly updated agent.

;; K-strategy snipes use individual learning to determine which size of mushrooms 
;; are nutritious.  This takes time and can involve eating many poisonous mushrooms.
;(defrecord KSnipe [id perceive mush-pref energy subenv x y age lifespan circled$ cfg-data$]
;  Propertied
;  (properties [original-snipe] (make-properties id cfg-data$))
;  Oriented2D
;  (orientation2D [this] (pref-orientation -0.0004 0.0004 (:mush-pref this))) ; TODO FIX THESE HARCODED VALUES?
;  Object
;  (toString [_] (str "<KSnipe #" id">")))

;; Social snipes learn from the preferences of other nearby snipes.
;(defrecord SSnipe [id perceive mush-pref energy subenv x y age lifespan circled$ cfg-data$]
;  Propertied
;  (properties [original-snipe] (make-properties id cfg-data$))
;  Oriented2D
;  (orientation2D [this] (pref-orientation -0.0004 0.0004 (:mush-pref this))) ; TODO FIX THESE HARCODED VALUES?
;  Object
;  (toString [_] (str "<SSnipe #" id">")))

  ;  (let [extreme-pref (:extreme-pref @(:cfg-data$ this))] ; can I pull this out so doesn't have to run every time for every snipe?
  ;    (pref-orientation (- extreme-pref) extreme-pref (:mush-pref this))))

;; r-strategy snipes don't learn: They go right to work eating their preferred
;; size mushrooms, which may be the poisonous kind in their environment--or not.
;; Their children might have either size preference.  This means that the ones
;; that have the "right" preference can usually reproduce more quickly than k-snipes.
(defrecord RSnipe [id mush-pref energy subenv x y age lifespan circled$ cfg-data$] ; r-snipe that prefers small mushrooms
  Propertied
  (properties [original-snipe] (make-properties id cfg-data$))
  Object
  (toString [this] (str "<RSnipe #" id ">")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; SNIPE MAKER FUNCTIONS

(defn calc-lifespan
  [rng cfg-data]
  (let [mean (:lifespan-mean cfg-data)
        sd (:lifespan-sd cfg-data)]
    (if (pos? mean)
      (math/round (ran/next-gaussian rng mean sd))
      0)))

;(defn make-k-snipe 
;  [rng cfg-data$ energy subenv new-id x y]
;  (KSnipe. new-id
;           perc/k-snipe-pref ; perceive: function for responding to mushrooms
;           0.0               ; mush-pref begins with indifference
;           energy            ; initial energy level
;           subenv            ; :west or :east
;           x y               ; location of snipe on grid
;           0                 ; age of snipe
;           (calc-lifespan rng @cfg-data$) ; lifespan
;           (atom false)      ; is snipe displayed circled in the GUI?
;           cfg-data$))       ; contains global parameters for snipe operation

(defn make-r-snipe
  [rng cfg-data$ energy subenv new-id x y]
  (let [extreme-pref (:extreme-pref @cfg-data$)]
    (if (< (ran/next-double rng) 0.5)
      (RSnipe. new-id (- extreme-pref) energy subenv x y 0 (calc-lifespan rng @cfg-data$) (atom false) cfg-data$)
      (RSnipe. new-id extreme-pref energy subenv x y 0 (calc-lifespan rng @cfg-data$) (atom false) cfg-data$))))

;(defn make-s-snipe 
;  [rng cfg-data$ energy subenv new-id x y]
;  (SSnipe. new-id
;           perc/s-snipe-pref ; use simple r-snipe method but a different starting strategy
;           0.0               ; will be set soon by s-snipe-pref
;           energy
;           subenv
;           x y
;           0
;           (calc-lifespan rng @cfg-data$)
;           (atom false)
;           cfg-data$))

;(defn make-newborn-k-snipe 
;  [rng cfg-data$ subenv new-id x y]
;  (let [{:keys [initial-energy]} @cfg-data$]
;    (make-k-snipe rng cfg-data$ initial-energy subenv new-id x y)))

(defn make-newborn-r-snipe
  [rng cfg-data$ subenv new-id x y]
  (let [{:keys [initial-energy]} @cfg-data$]
    (make-r-snipe rng cfg-data$ initial-energy subenv new-id x y)))

;(defn make-newborn-s-snipe 
;  [rng cfg-data$ subenv new-id x y]
;  (let [{:keys [initial-energy]} @cfg-data$]
;    (make-s-snipe rng cfg-data$ initial-energy subenv new-id x y)))

;(defn make-rand-k-snipe 
;  "Create k-snipe with random energy (from rand-energy)."
;  [rng cfg-data$ subenv new-id x y]
;  (make-k-snipe rng cfg-data$ (rand-energy rng @cfg-data$) subenv new-id x y))

(defn make-rand-r-snipe 
  "Create r-snipe with random energy (from rand-energy)."
  [rng cfg-data$ subenv new-id x y]
  (make-r-snipe rng cfg-data$ (rand-energy rng @cfg-data$) subenv new-id x y))

;(defn make-rand-s-snipe 
;  "Create s-snipe with random energy (from rand-energy)."
;  [rng cfg-data$ subenv new-id x y]
;  (make-s-snipe rng cfg-data$ (rand-energy rng @cfg-data$) subenv new-id x y))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; MAKE-PROPERTIES FUNCTION

;; Used by GUI to allow inspectors to follow a fnlly updated agent.
;; (Code below makes use of the fact that in Clojure, vectors can be treated as functions
;; of indexes, returning the indexed item; that keywords such as :x can be treated as 
;; functions of maps; and that defrecords such as snipes can be treated as maps.)
(defn make-properties
  "Return a Properties subclass for use by Propertied's properties method so
  that certain fields can be displayed in the GUI on request."
  [id cfg-data$]
  ;; These definitions need to be coordinated by hand:
  (let [kys [:energy :mush-pref :subenv :x :y :age :lifespan :circled$] ; TODO CHANGE FOR NEW FIELDS
        circled-idx 7 ; HARDCODED INDEX for circled$ field              ; TODO CHANGE FOR NEW FIELDS
        descriptions ["Energy is what snipes get from mushrooms."       ; TODO CHANGE FOR NEW FIELDS
                      "Preference for large (positive number) or small (negative number) mushrooms."
                      "Name of snipe's subenv"
                      "x coordinate in underlying grid"
                      "y coordinate in underlying grid"
                      "Age of snipe"
                      "Maximum age"
                      "Boolean indicating whether circled in GUI"]
        types [java.lang.Double java.lang.Double java.lang.String java.lang.Integer java.lang.Integer ; TODO CHANGE FOR NEW FIELDS
               java.lang.Integer java.lang.Integer java.lang.Boolean]                                 ; TODO CHANGE FOR NEW FIELDS
        read-write [false false false false false false false true] ; allow user to turn off circled in UI ; TODO CHANGE FOR NEW FIELDS
        names (mapv name kys)
        num-properties (count kys)
        hidden     (vec (repeat num-properties false)) ; no properties specified here are to be hidden from GUI
        get-curr-snipe (fn [] ((:snipe-map (:popenv @cfg-data$)) id))] ; find current version of this snipe
    (reset! (:circled$ (get-curr-snipe)) true) ; make-properties is only called by inspector, in which case highlight snipe in UI
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

;; note underscores
;(defn k-snipe? [s] (instance? example.snipe.KSnipe s))
(defn r-snipe? [s] (instance? example.snipe.RSnipe s))
;(defn s-snipe? [s] (instance? example.snipe.SSnipe s))

;; Bottleneck with threads?
;(defn next-id 
;  "Returns a unique integer for use as an id."
;  [] 
;  (Long. (str (gensym ""))))

; ;; OK THIS IS A BAD IDEA.  First, the initial def and .set runs in a different
; ;; thread than the next-id calls.  That is fixed with the when-not that
; ;; I added.  BUT THERE ARE NEW THREADS CREATED WHILE A RUN IS RUNNING,
; ;; at least somes when you pause and restart the simulation.  So this 
; ;; causes the numbering to restart with 1 over and over again within a
; ;; run.
; ;; 
; ;; See https://stackoverflow.com/a/21608858/1455243
; ;; Or use amalloy's wrapper:
; ;; https://stackoverflow.com/a/7391714/1455243
; ;; https://github.com/flatland/useful/blob/5de8a2ff32d351dcc931d0d10cdd4d67797bdc42/src/flatland/useful/utils.clj#L201
; (def thread-prev-id$ (ThreadLocal.))
; (.set thread-prev-id$ (atom 0))
; (defn next-id 
;   "Returns a unique thread-local integer for use as an id.  (Ids are 
;   not reset at the beginning of subsequent runs in the same Java thread
;   but if the application is started using MASON's -parallel flag, there
;   will be a different sequence of ids in each thread.)"
;   [] 
;   (when-not (.get thread-prev-id$) 
;     (.set thread-prev-id$ (atom 0)))
;   (swap! (.get thread-prev-id$) inc))

;; Alt version that I thought would be better, but it's not:
;; Simple, non-gensym version means that max id tracks total number 
;; of snipes that have lived.  Using a closure is an efficient way
;; to make the id thread-local.  Storing it in the Sim instance data
;; seems like a bit much.  FIXME NO it's not thread-local.  The function
;; is global, so all parallel sessions will increment the same atom,
;; just as when I made the atom global.  Note this is not a serious problem
;; but it does mean that snipe id's don't reflect numbers of snipes in
;; a single run.
;(def ^{:doc "Returns the next integer for use as a snipe id."} next-id
;  (let [prev-id$ (atom 0)]
;    (fn [] (swap! prev-id$ inc))))

;; Does gensym avoid bottleneck??
;(defn next-id 
;  "Returns a unique integer for use as an id."
;  [] 
;  (Long. (str (gensym ""))))
