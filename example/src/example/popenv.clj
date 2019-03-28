;; This software is copyright 2016, 2017, 2018, 2019 by Marshall Abrams, 
;; and is distributed under the Gnu General Public License version 3.0 
;; as specified in the the file LICENSE.

;(set! *warn-on-reflection* true)

(ns example.popenv
  (:require [example.snipe :as sn]
            ;[example.mush :as mu]
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
         add-organism-to-rand-loc!  ;add-mush!  maybe-add-mush! add-mushs! 
         move-snipes move-snipe!  choose-next-loc ;perceive-mushroom 
         add-to-energy eat-if-appetizing snipes-eat snipes-die snipes-reproduce
         cull-snipes cull-snipes-to-max age-snipes excess-snipes snipes-in-subenv 
         obey-carrying-capacity add-snipes! add-snipes-to-min)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; TOP LEVEL FUNCTIONS

(defrecord SubEnv [snipe-field   ; ObjectGrid2D
                   ;mush-field    ; ObjectGrid2D
                   dead-snipes]) ; keep a record of dead snipes for later stats

(defrecord PopEnv [west 
                   ;east 
                   snipe-map curr-snipe-id$]) ; two SubEnvs, and map from ids to snipes

(defn setup-popenv-config!
  [cfg-data$]
  (let [{:keys [env-width env-height carrying-proportion mush-low-size mush-high-size]} @cfg-data$]
    ;(swap! cfg-data$ assoc :mush-size-scale (/ 1.0 (- mush-high-size mush-low-size)))
    ;(swap! cfg-data$ assoc :mush-mid-size (/ (+ mush-low-size mush-high-size) 2.0))
    (swap! cfg-data$ assoc :max-subenv-pop-size (int (* env-width env-height carrying-proportion)))))

(defn make-subenv
  "Returns new SubEnv with mushs and snipes.  subenv-key is :west or :east."
  [rng cfg-data$ subenv-key curr-snipe-id$]
  (let [cfg-data @cfg-data$
        {:keys [env-width env-height]} cfg-data
        snipe-field (ObjectGrid2D. env-width env-height)
        ;mush-field  (ObjectGrid2D. env-width env-height)
	]
    ;(.clear mush-field)
    ;(add-mushs! rng cfg-data mush-field subenv-key)
    (.clear snipe-field)
    ;(add-snipes! rng cfg-data$ snipe-field subenv-key (:num-k-snipes cfg-data) sn/make-rand-k-snipe curr-snipe-id$)
    (add-snipes! rng cfg-data$ snipe-field subenv-key (:num-r-snipes cfg-data) sn/make-rand-r-snipe curr-snipe-id$)
    ;(add-snipes! rng cfg-data$ snipe-field subenv-key (:num-s-snipes cfg-data) sn/make-rand-s-snipe curr-snipe-id$)
    (SubEnv. snipe-field [])))

(defn make-snipe-map
  "Make a map from snipe ids to snipes."
  [^ObjectGrid2D west-snipe-field] ; ^ObjectGrid2D east-snipe-field
  (into {} (map #(vector (:id %) %)) ; transducer w/ vector: may be slightly faster than alternatives
        (concat (.elements west-snipe-field)
                ;(.elements east-snipe-field)
                ))) ; btw cost compared to not constructing a snipes map is trivial

(defn make-popenv
  [rng cfg-data$]
  (let [curr-snipe-id$ (atom 0)
        west (make-subenv rng cfg-data$ :west curr-snipe-id$)
        ;east (make-subenv rng cfg-data$ :east curr-snipe-id$)
        ]
    (PopEnv. west 
             ;east
             (make-snipe-map (:snipe-field west)
                             ;(:snipe-field east)
                             )
             curr-snipe-id$)))

(defn next-snipe-id
  [curr-snipe-id$]
  (swap! curr-snipe-id$ inc))

;(defn eat
;  "Wrapper for snipes-eat."
;  [rng cfg-data subenv]
;  (let [{:keys [snipe-field mush-field dead-snipes]} subenv
;        [snipe-field' mush-field'] (snipes-eat rng cfg-data snipe-field mush-field)]
;    (SubEnv. snipe-field' 
;             mush-field' 
;             dead-snipes)))

(defn die-move-spawn
  "Remove snipes that have no energy or are too old, cull snipes or increase 
  their number with newborn snipes if there is a population size specification,
  cull snipes if carrying capacity is exceeded, move snipes, increment snipe ages."
  [rng cfg-data$ subenv subenv-key curr-snipe-id$]
  ;; Note that order of bindings below is important.  e.g. we shouldn't worry
  ;; about carrying capacity until energy-less snipes have been removed.
  (let [cfg-data @cfg-data$
        {:keys [snipe-field ]} subenv ; mush-field dead-snipes
        ;[snipe-field' newly-died] (snipes-die cfg-data snipe-field)
        ;snipe-field' (add-snipes-to-min rng cfg-data$ snipe-field' :k-min-pop-sizes sn/k-snipe? subenv-key sn/make-rand-k-snipe curr-snipe-id$)
        ;snipe-field' (add-snipes-to-min rng cfg-data$ snipe-field :r-min-pop-sizes sn/r-snipe? subenv-key sn/make-rand-r-snipe curr-snipe-id$)
        ;snipe-field' (add-snipes-to-min rng cfg-data$ snipe-field' :s-min-pop-sizes sn/s-snipe? subenv-key sn/make-rand-s-snipe curr-snipe-id$)
        ;[snipe-field' k-newly-culled] (cull-snipes-to-max rng cfg-data$ snipe-field' :k-max-pop-sizes sn/k-snipe?)
        ;[snipe-field' r-newly-culled] (cull-snipes-to-max rng cfg-data$ snipe-field' :r-max-pop-sizes sn/r-snipe?)
        ;[snipe-field' s-newly-culled] (cull-snipes-to-max rng cfg-data$ snipe-field' :s-max-pop-sizes sn/s-snipe?)
        ;[snipe-field' carrying-newly-culled] (obey-carrying-capacity rng cfg-data snipe-field')
        snipe-field' (move-snipes rng cfg-data snipe-field)     ; only the living get to move
        ;snipe-field' (age-snipes snipe-field')
        ]
    (SubEnv. snipe-field' 
             ;mush-field 
             []
             ;(conj dead-snipes  ; each timestep adds a separate collection of dead snipes
             ;      (concat newly-died
             ;              ;k-newly-culled 
             ;              r-newly-culled 
             ;              ;s-newly-culled
             ;              carrying-newly-culled))
             )))

(defn next-popenv
  "Given an rng, a simConfigData atom, and a SubEnv, return a new SubEnv for
  the next time step.  Snipes eat, reproduce, die, and move."
  [popenv rng cfg-data$]
  (let [{:keys [west curr-snipe-id$]} popenv ;east 
        ;west' (eat rng @cfg-data$ west) ; better to eat before reproduction--makes sense
        ;east' (eat rng @cfg-data$ east) ; and avoids complexity with max energy
        ;[west-snipe-field' east-snipe-field'] (snipes-reproduce rng cfg-data$ ; uses both fields: newborns could go anywhere
        ;                                                        (:snipe-field west')
        ;                                                        ;(:snipe-field east')
	;							curr-snipe-id$)
        west' (die-move-spawn rng cfg-data$ (assoc west :snipe-field (:snipe-field west)) :west curr-snipe-id$)
        ;east' (die-move-spawn rng cfg-data$ (assoc east' :snipe-field east-snipe-field') :east curr-snipe-id$)
        snipe-map' (make-snipe-map (:snipe-field west') ;(:snipe-field east')
                                   )]
    (PopEnv. west' snipe-map' curr-snipe-id$))); east' 

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
