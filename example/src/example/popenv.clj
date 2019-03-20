;; This software is copyright 2016, 2017, 2018, 2019 by Marshall Abrams, 
;; and is distributed under the Gnu General Public License version 3.0 
;; as specified in the the file LICENSE.

;(set! *warn-on-reflection* true)

(ns example.popenv
  (:require [example.snipe :as sn]
            [example.mush :as mu]
            [utils.random :as ran]
            [utils.random-utils :as ranu]
            [clojure.math.numeric-tower :as nmath])
  (:import [sim.field.grid Grid2D ObjectGrid2D]
           [sim.util IntBag Bag]
           [ec.util MersenneTwisterFast]
           [java.util Collection])) ; for a type hint below

;; Conventions:
;; * Adding an apostrophe to a var name--e.g. making x into x-prime--means
;;   that this is the next value of that thing.  Sometimes this name will
;;   be reused repeatedly in a let as the value is sequentially updated.
;; * Var names containing atoms have "$" as a suffix.

;(use '[clojure.pprint]) ; DEBUG

(declare setup-popenv-config! make-popenv next-popenv organism-setter 
         add-organism-to-rand-loc!  add-mush!  maybe-add-mush! add-mushs! 
         move-snipes move-snipe!  choose-next-loc perceive-mushroom 
         add-to-energy eat-if-appetizing snipes-eat snipes-die snipes-reproduce
         cull-snipes cull-snipes-to-max age-snipes excess-snipes snipes-in-subenv 
         obey-carrying-capacity add-snipes! add-snipes-to-min)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; TOP LEVEL FUNCTIONS

(defrecord SubEnv [snipe-field   ; ObjectGrid2D
                   mush-field    ; ObjectGrid2D
                   dead-snipes]) ; keep a record of dead snipes for later stats

(defrecord PopEnv [west east snipe-map curr-snipe-id$]) ; two SubEnvs, and map from ids to snipes

(defn setup-popenv-config!
  [cfg-data$]
  (let [{:keys [env-width env-height carrying-proportion mush-low-size mush-high-size]} @cfg-data$]
    (swap! cfg-data$ assoc :mush-size-scale (/ 1.0 (- mush-high-size mush-low-size)))
    (swap! cfg-data$ assoc :mush-mid-size (/ (+ mush-low-size mush-high-size) 2.0))
    (swap! cfg-data$ assoc :max-subenv-pop-size (int (* env-width env-height carrying-proportion)))))

(defn make-subenv
  "Returns new SubEnv with mushs and snipes.  subenv-key is :west or :east."
  [rng cfg-data$ subenv-key curr-snipe-id$]
  (let [cfg-data @cfg-data$
        {:keys [env-width env-height]} cfg-data
        snipe-field (ObjectGrid2D. env-width env-height)
        mush-field  (ObjectGrid2D. env-width env-height)]
    (.clear mush-field)
    (add-mushs! rng cfg-data mush-field subenv-key)
    (.clear snipe-field)
    (add-snipes! rng cfg-data$ snipe-field subenv-key (:num-k-snipes cfg-data) sn/make-rand-k-snipe curr-snipe-id$)
    (add-snipes! rng cfg-data$ snipe-field subenv-key (:num-r-snipes cfg-data) sn/make-rand-r-snipe curr-snipe-id$)
    (add-snipes! rng cfg-data$ snipe-field subenv-key (:num-s-snipes cfg-data) sn/make-rand-s-snipe curr-snipe-id$)
    (SubEnv. snipe-field mush-field [])))

(defn make-snipe-map
  "Make a map from snipe ids to snipes."
  [^ObjectGrid2D west-snipe-field ^ObjectGrid2D east-snipe-field]
  (into {} (map #(vector (:id %) %)) ; transducer w/ vector: may be slightly faster than alternatives
        (concat (.elements west-snipe-field)
                (.elements east-snipe-field)))) ; btw cost compared to not constructing a snipes map is trivial

(defn make-popenv
  [rng cfg-data$]
  (let [curr-snipe-id$ (atom 0)
        west (make-subenv rng cfg-data$ :west curr-snipe-id$)
        east (make-subenv rng cfg-data$ :east curr-snipe-id$)]
    (PopEnv. west 
             east
             (make-snipe-map (:snipe-field west)
                             (:snipe-field east))
             curr-snipe-id$)))

(defn next-snipe-id
  [curr-snipe-id$]
  (swap! curr-snipe-id$ inc))

(defn eat
  "Wrapper for snipes-eat."
  [rng cfg-data subenv]
  (let [{:keys [snipe-field mush-field dead-snipes]} subenv
        [snipe-field' mush-field'] (snipes-eat rng cfg-data snipe-field mush-field)]
    (SubEnv. snipe-field' 
             mush-field' 
             dead-snipes)))

(defn die-move-spawn
  "Remove snipes that have no energy or are too old, cull snipes or increase 
  their number with newborn snipes if there is a population size specification,
  cull snipes if carrying capacity is exceeded, move snipes, increment snipe ages."
  [rng cfg-data$ subenv subenv-key curr-snipe-id$]
  ;; Note that order of bindings below is important.  e.g. we shouldn't worry
  ;; about carrying capacity until energy-less snipes have been removed.
  (let [cfg-data @cfg-data$
        {:keys [snipe-field mush-field dead-snipes]} subenv
        [snipe-field' newly-died] (snipes-die cfg-data snipe-field)
        snipe-field' (add-snipes-to-min rng cfg-data$ snipe-field' :k-min-pop-sizes sn/k-snipe? subenv-key sn/make-rand-k-snipe curr-snipe-id$)
        snipe-field' (add-snipes-to-min rng cfg-data$ snipe-field' :r-min-pop-sizes sn/r-snipe? subenv-key sn/make-rand-r-snipe curr-snipe-id$)
        snipe-field' (add-snipes-to-min rng cfg-data$ snipe-field' :s-min-pop-sizes sn/s-snipe? subenv-key sn/make-rand-s-snipe curr-snipe-id$)
        [snipe-field' k-newly-culled] (cull-snipes-to-max rng cfg-data$ snipe-field' :k-max-pop-sizes sn/k-snipe?)
        [snipe-field' r-newly-culled] (cull-snipes-to-max rng cfg-data$ snipe-field' :r-max-pop-sizes sn/r-snipe?)
        [snipe-field' s-newly-culled] (cull-snipes-to-max rng cfg-data$ snipe-field' :s-max-pop-sizes sn/s-snipe?)
        [snipe-field' carrying-newly-culled] (obey-carrying-capacity rng cfg-data snipe-field')
        snipe-field' (move-snipes rng cfg-data snipe-field')     ; only the living get to move
        snipe-field' (age-snipes snipe-field')]
    (SubEnv. snipe-field' 
             mush-field 
             (conj dead-snipes  ; each timestep adds a separate collection of dead snipes
                   (concat newly-died
                           k-newly-culled r-newly-culled s-newly-culled
                           carrying-newly-culled)))))

(defn next-popenv
  "Given an rng, a simConfigData atom, and a SubEnv, return a new SubEnv for
  the next time step.  Snipes eat, reproduce, die, and move."
  [popenv rng cfg-data$]
  (let [{:keys [west east curr-snipe-id$]} popenv
        west' (eat rng @cfg-data$ west) ; better to eat before reproduction--makes sense
        east' (eat rng @cfg-data$ east) ; and avoids complexity with max energy
        [west-snipe-field' east-snipe-field'] (snipes-reproduce rng cfg-data$ ; uses both fields: newborns could go anywhere
                                                                (:snipe-field west')
                                                                (:snipe-field east')
								curr-snipe-id$)
        west' (die-move-spawn rng cfg-data$ (assoc west' :snipe-field west-snipe-field') :west curr-snipe-id$)
        east' (die-move-spawn rng cfg-data$ (assoc east' :snipe-field east-snipe-field') :east curr-snipe-id$)
        snipe-map' (make-snipe-map (:snipe-field west') (:snipe-field east'))]
    (PopEnv. west' east' snipe-map' curr-snipe-id$)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; CREATE AND PLACE ORGANISMS

(defn organism-setter
  [organism-maker]
  (fn [^ObjectGrid2D field x y]
    (.set field x y (organism-maker x y))))

(defn add-organism-to-rand-loc!
  "Create and add an organism to field using organism-setter!, which expects 
  x and y coordinates as arguments.  Looks for an empty location, so could
  be inefficient if a large proportion of cells in the field are filled.
  If :left or :right is passed for subenv, only looks for locations in
  the left or right portion of the world."
  [rng cfg-data ^ObjectGrid2D field width height organism-setter!]
  (loop []
    (let [x (ran/rand-idx rng width)
          y (ran/rand-idx rng height)]
      (if-not (.get field x y) ; don't clobber another organism; empty slots contain Java nulls, i.e. Clojure nils
        (organism-setter! field x y)
        (recur)))))

(defn add-snipes!
 "Add snipes to field at random locations.  snipe-num-key is a key for 
 the number of snipes of a given type to make, and snipe-maker is a function
 that will make an individual snipe."
 [rng cfg-data$ field subenv-key num-to-add snipe-maker curr-snipe-id$]
 (let [cfg-data @cfg-data$
       {:keys [env-width env-height]} cfg-data]
  (dotimes [_ num-to-add] ; don't use lazy method--it may never be executed
   (add-organism-to-rand-loc! rng cfg-data field env-width env-height 
    (organism-setter (partial snipe-maker rng cfg-data$ subenv-key (next-snipe-id curr-snipe-id$)))))))

;; Do I really need so many mushrooms?  They don't change.  Couldn't I just define four mushrooms,
;; and reuse them?  (If so, be careful about their deaths.)
(defn add-mushs!
  "For each patch in mush-field, optionally add a new mushroom, with 
  probability (:mush-prob cfg-data).  NOTE: Doesn't check for an existing
  mushroom in the patch: Will simply clobber whatever mushroom is there,
  if any."
  [rng cfg-data field subenv-key]
  (let [{:keys [env-width env-height]} cfg-data]
    (doseq [x (range env-width)
            y (range env-height)]
      (maybe-add-mush! rng cfg-data field x y subenv-key))))

(defn maybe-add-mush!
  [rng cfg-data field x y subenv-key]
  (when (< (ran/next-double rng) (:mush-prob cfg-data)) ; flip biased coin to decide whether to grow a mushroom
    (add-mush! rng cfg-data field x y subenv-key)))

(defn add-mush!
  "Adds a mushroom to a random location in field.  subenv, which is 
  :west or :east, determines which size is associated 
  with which nutritional value."
  [rng cfg-data ^ObjectGrid2D field x y subenv-key]
  (let [{:keys [mush-low-size mush-high-size 
                mush-sd mush-pos-nutrition mush-neg-nutrition]} cfg-data
        [low-mean-nutrition high-mean-nutrition] (if (= subenv-key :west) ; subenv determines whether low vs high reflectance
                                                   [mush-pos-nutrition mush-neg-nutrition]   ; paired with
                                                   [mush-neg-nutrition mush-pos-nutrition])] ; nutritious vs poison
    (.set field x y 
          (if (< (ran/next-double rng) 0.5) ; half the mushrooms are of each kind in each subenv, on average
            (mu/make-mush mush-low-size  mush-sd low-mean-nutrition rng)
            (mu/make-mush mush-high-size mush-sd high-mean-nutrition rng)))))

;; Use this for adding new mushrooms to replace those eaten.  Since
;; over time, the population will tend to eat mostly nutritious mushrooms,
;; if we randomly assign nutritiousness to replacement mushrooms, there
;; will gradual loss of nutritious mushrooms.  This function instead
;; keeps the frequencies the same.  Note that this just moves the eaten
;; mushroom, unchanged, to a new location.
(defn replace-mush!
  "Adds a mush just like old-mush but with a new id.  Note that x must
  be in the same subenv as the one from which the old mush came.  Otherwise
  size and nutrition won't match up properly."
  [old-mush ^ObjectGrid2D field x y]
  (.set field x y old-mush))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; MOVEMENT

(defn move-snipes
  [rng cfg-data ^ObjectGrid2D snipe-field]
  (let [{:keys [env-width env-height]} cfg-data
        snipes (.elements snipe-field)
        loc-snipe-vec-maps (for [snipe snipes  ; make seq of maps with a coord pair as key and singleton seq containing snipe as val
                                 :let [next-loc (choose-next-loc rng snipe-field snipe)]] ; can be current loc
                             next-loc)
        loc-snipe-vec-map (apply merge-with concat loc-snipe-vec-maps) ; convert sec of maps to a single map where snipe-vecs with same loc are concatenated
        loc-snipe-map (into {}                                       ; collect several maps into one
                            (for [[coords snipes] loc-snipe-vec-map] ; go through key-value pairs, where values are collections containing one or more snipes
                              (let [len (count snipes)]              ; convert to key-value pairs where value is a snipe
                                (if (= len 1)
                                  [coords (first snipes)]                          ; when more than one
                                  (let [mover (nth snipes (ran/rand-idx rng len))] ; randomly select one to move
                                    (into {coords mover} (map (fn [snipe] {[(:x snipe) (:y snipe)] snipe}) ; and make others "move" to current loc
                                                              (remove #(= mover %) snipes))))))))         ; (could be more efficient to leave them alone)
        new-snipe-field (ObjectGrid2D. env-width env-height)]
    ;; Since we have a collection of all new snipe positions, including those
    ;; who remained in place, we can just place them on a fresh snipe-field:
    (doseq [[[x y] snipe] loc-snipe-map] ; will convert the map into a sequence of mapentries, which are seqs
      (move-snipe! new-snipe-field x y snipe))
    new-snipe-field))

;; doesn't delete old snipe ref--designed to be used on an empty snipe-field:
(defn move-snipe!
  [^ObjectGrid2D snipe-field x y snipe]
  (.set snipe-field x y (assoc snipe :x x :y y)))

;; Formerly I made top-level reusable IntBags for choose-next-loc.
;; I think this is the source of an ArrayIndexOutOfBoundsException
;; when I the MASON -parallel option.  So now the IntBags are local.

(defn choose-next-loc
  "Return a pair of field coordinates randomly selected from the empty 
  hexagonally neighboring locations of snipe's location, or the current
  location if all neighboring locations are filled."
  [rng ^ObjectGrid2D snipe-field snipe]
  (let [curr-x (:x snipe)
        curr-y (:y snipe)
	x-coord-bag (IntBag. 6)
	y-coord-bag (IntBag. 6)]
    (.getHexagonalLocations snipe-field              ; inserts coords of neighbors into x-pos and y-pos args
                            curr-x curr-y
                            1 Grid2D/TOROIDAL false  ; immediate neighbors, toroidally, don't include me
                            x-coord-bag y-coord-bag) ; will hold coords of neighbors
    (let [candidate-locs (for [[x y] (map vector 
                                          (.toIntegerArray x-coord-bag)  ; x-pos, y-pos have to be IntBags
                                          (.toIntegerArray y-coord-bag)) ; but these are not ISeqs like Java arrays
                               :when (not (.get snipe-field x y))] ; when cell is empty
                           [x y])]
      (if (seq candidate-locs) ; when not empty
        (let [len (count candidate-locs)
              idx (ran/rand-idx rng len)
              [next-x next-y] (nth candidate-locs idx)]
          {[next-x next-y] [snipe]}) ; key is a pair of coords; val is a single-element vector containing a snipe
        {[curr-x curr-y] [snipe]}))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; PERCEPTION AND EATING

(defn snipes-eat
  "Snipes on mushrooms eat, if they decide to do so."
  [rng cfg-data ^ObjectGrid2D snipe-field ^ObjectGrid2D mush-field]
  (let [{:keys [env-center env-width env-height max-energy]} cfg-data
        snipes (.elements snipe-field)
        snipes-plus-eaten? (for [snipe snipes    ; returns only snipes on mushrooms
                                 :let [{:keys [x y]} snipe
                                       mush (.get mush-field x y)]
                                 :when mush]
                             (eat-if-appetizing rng max-energy snipe mush))
        new-snipe-field (ObjectGrid2D. snipe-field) ; new field that's a copy of old one
        new-mush-field  (ObjectGrid2D. mush-field)]
    (doseq [[snipe ate?] snipes-plus-eaten? :when ate?]
      (let [{:keys [x y]} snipe
            eaten-mush (.get mush-field x y)]
        (.set new-snipe-field x y snipe) ; replace old snipe with new, more experienced snipe, or maybe the same one
        (.set new-mush-field x y nil)    ; mushroom has been eaten
        ;; a new mushroom grows elsewhere:
        (add-organism-to-rand-loc! rng cfg-data new-mush-field env-width env-height 
                                   (partial replace-mush! eaten-mush)))) ; See comment at replace-mush!
    [new-snipe-field new-mush-field]))

(defn eat-if-appetizing 
  "Returns a pair containing (a) a new version of the snipe, with 
  an updated cognitive state, if appropriate, and an updated 
  energy level if eating occured; and (b) a boolean indicating
  whether eating occurred.  Step (a) is performed by the perceive
  function contained within the snipe.  This function is called with
  two arguments--the snipe itself, and the mushroom.  The function
  should return a snipe updated to reflect its new experience, and
  a boolean indicating whether the mushroom is to be eaten."
  [rng max-energy snipe mush] 
  (let [[experienced-snipe appetizing?] ((:perceive snipe) rng snipe mush)]
    (if appetizing?
      [(update experienced-snipe :energy add-to-energy max-energy (:nutrition mush))
       true]
      [experienced-snipe false])))

(defn add-to-energy
  [snipe-energy max-energy mush-nutrition]
  (max 0                ; negative energy is impossible
       (min max-energy  ; can't exceed max energy
            (+ snipe-energy mush-nutrition))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; BIRTH AND DEATH

(defn snipes-die
  "Returns a new snipe-field with zero-energy and elderly snipes removed."
  [cfg-data ^ObjectGrid2D snipe-field]
  (let [{:keys [env-width env-height]} cfg-data
        new-snipe-field (ObjectGrid2D. env-width env-height)
        [live-snipes newly-dead] (reduce (fn [[living dead] snipe]
                                           (let [lifespan (:lifespan snipe)]
                                             (if (and (or (zero? lifespan)           ; if there's a nonzero lifespan set
                                                          (< (:age snipe) lifespan)) ; then if snipe is still young enough to live
                                                      (> (:energy snipe) 0))         ; and snipe has energy left
                                               [(conj living snipe) dead]            ; add put it in the living set
                                               [living (conj dead snipe)])))          ; otherwise add it to the dead
                                         [[][]]
                                         (.elements snipe-field))] ; old version: (remove #(<= (:energy %) 0) (.elements snipe-field))]
    (doseq [snipe live-snipes]
      (.set new-snipe-field (:x snipe) (:y snipe) snipe)) ; add the living to the fresh snipe field
    [new-snipe-field newly-dead]))

(defn obey-carrying-capacity
  [rng cfg-data ^ObjectGrid2D snipe-field]
  (let [snipes (.elements snipe-field)
        target-size (:max-subenv-pop-size cfg-data)
        num-to-cull (- (count snipes) target-size)]
    (if (pos? num-to-cull)
      (cull-snipes rng snipe-field snipes num-to-cull)
      [snipe-field nil])))

(defn cull-snipes-to-max
  "If the max map in cfg-data for snipe-map-key exists and has an entry for
  the current step, return a new snipe field with a reduced number of 
  those snipes (the ones that satisfy snipe-pred), making their number
  the value of this step in the max map.  Otherwise return the original 
  snipe field unchanged."
  [rng cfg-data$ ^ObjectGrid2D snipe-field snipe-map-key snipe-pred]
  (let [cfg-data @cfg-data$
        max-map (snipe-map-key cfg-data)]
    (if-let [target-subpop-size (and max-map ; nil if no map or this step not in map
                                     (get max-map (:curr-step cfg-data)))] ; use get: it might be a java Map
      (let [snipes (filterv snipe-pred (.elements snipe-field))
            num-to-cull (- (count snipes) target-subpop-size)]
        (if (pos? num-to-cull)
          (cull-snipes rng snipe-field snipes num-to-cull) ; too many snipes
          [snipe-field nil])) ; what would be needed to add snipes here: (add-snipes rng cfg-data$ snipe-field SUBENV-KEY (- num-to-cull) SNIPE-MAKER CURR-SNIPE-ID$)
      [snipe-field nil])))

(defn cull-snipes
  "Return a vector containing a new snipe-field like the old one but with 
  the number of element in snipes randomly reduced to target-size, and a
  collection of removed snipes.  Assumes the number of snipes is at least
  as great as target-size."
  [rng ^ObjectGrid2D snipe-field snipes num-to-cull]
  (let [excess-snipes (ranu/sample-without-repl rng num-to-cull (seq snipes)) ; choose random snipes for removal
        new-snipe-field (ObjectGrid2D. snipe-field)]
    (doseq [snipe excess-snipes]
      (.set new-snipe-field (:x snipe) (:y snipe) nil)) ; remove chosen snipes
    [new-snipe-field excess-snipes]))

(defn add-snipes-to-min
  "If the min map in cfg-data for snipe-map-key exists and has an entry for
  the current step, return a new snipe field with added newborn snipes, so
  that the number of the specified variety of snipes in the subpop is 
  increased to the value of this step in the min map.  Otherwise return
  the original snipe field unchanged."
  [rng cfg-data$ ^ObjectGrid2D snipe-field snipe-map-key snipe-pred subenv-key snipe-maker curr-snipe-id$]
  (let [cfg-data @cfg-data$
        min-map (snipe-map-key cfg-data)]
    (if-let [target-subpop-size (and min-map ; nil if no map or this step not in map
                                     (get min-map (:curr-step cfg-data)))] ; use get: it might be a java Map
      (let [snipes (filterv snipe-pred (.elements snipe-field))
            num-to-add (- target-subpop-size (count snipes))]
        (if (pos? num-to-add)
          (let [new-snipe-field (ObjectGrid2D. snipe-field)]
            (add-snipes! rng cfg-data$ new-snipe-field subenv-key num-to-add snipe-maker curr-snipe-id$)
            new-snipe-field)
          snipe-field))
      snipe-field)))

(defn give-birth
  [cfg-data snipe]
  (let [{:keys [birth-threshold birth-cost]} cfg-data
        old-energy (:energy snipe)]
    (if (< old-energy birth-threshold)
      [0 snipe]
      (let [num-births (+ 1 (quot (- old-energy birth-threshold) birth-cost)) ; one birth allowed by hitting threshold, plus additional allowed by energy exceeding threshold
            remaining-energy (- old-energy (* num-births birth-cost))]
        [num-births (assoc snipe :energy remaining-energy)]))))

(defn snipes-reproduce
  [^MersenneTwisterFast rng cfg-data$ ^ObjectGrid2D west-snipe-field ^ObjectGrid2D east-snipe-field curr-snipe-id$]
  (let [{:keys [env-width env-height birth-threshold birth-cost]} @cfg-data$
        suff-energy (fn [snipe] (>= (:energy snipe) birth-threshold))
        west-snipe-field' (ObjectGrid2D. west-snipe-field) ; new field that's a copy of old one
        east-snipe-field' (ObjectGrid2D. east-snipe-field)
        ^Collection west-mothers (filter suff-energy (.elements west-snipe-field))
        ^Collection east-mothers (filter suff-energy (.elements east-snipe-field))
        mothers (Bag. west-mothers)] ; MASON Bags have an in-place shuffle routine
    (.addAll mothers east-mothers)
    (.shuffle mothers rng)
    (doseq [snipe mothers]
      (let [[num-births snipe'] (give-birth @cfg-data$ snipe)
            parental-snipe-field' (if (= (:subenv snipe') :west)
                                    west-snipe-field'
                                    east-snipe-field')]
        ;; replace old snipe with one updated to reflect birth:
        (.set parental-snipe-field' (:x snipe') (:y snipe') snipe')
        ;; create and place newborns:
        (dotimes [_ num-births]
          (let [[child-snipe-field subenv-key] (if (< (ran/next-double rng) 0.5)   ; newborns are randomly 
                                                 [west-snipe-field' :west]  ; assigned to a subenv
                                                 [east-snipe-field' :east])]
            (add-organism-to-rand-loc! rng @cfg-data$ child-snipe-field env-width env-height ; add newborn of same type as parent
                                       (organism-setter (cond (sn/k-snipe? snipe') (partial sn/make-newborn-k-snipe rng cfg-data$ subenv-key 
				                                                            (next-snipe-id curr-snipe-id$))
                                                              (sn/r-snipe? snipe') (partial sn/make-newborn-r-snipe rng cfg-data$ subenv-key
				                                                            (next-snipe-id curr-snipe-id$))
                                                              :else                (partial sn/make-newborn-s-snipe rng cfg-data$ subenv-key
				                                                            (next-snipe-id curr-snipe-id$)))))))))
    [west-snipe-field' east-snipe-field']))

 ; newborn should be like parent
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; OTHER CHANGES

(defn age-snipes
  [^ObjectGrid2D snipe-field]
  (let [old-snipes (.elements snipe-field)
        new-snipe-field (ObjectGrid2D. snipe-field)]
    (doseq [snipe old-snipes]
      (.set new-snipe-field (:x snipe) (:y snipe) (update snipe :age inc)))
    new-snipe-field))
