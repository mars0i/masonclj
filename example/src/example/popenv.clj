;; This software is copyright 2016, 2017, 2018, 2019, 2022 by Marshall Abrams, 
;; and is distributed under the Gnu General Public License version 3.0 
;; as specified in the the file LICENSE.

;(set! *warn-on-reflection* true)

(ns example.popenv
  (:require [example.snipe :as sn]
            [utils.random :as ran]
            [utils.random-utils :as ranu]
            [clojure.math.numeric-tower :as nmath])
  (:import [sim.field.grid Grid2D ObjectGrid2D]
           [sim.util IntBag Bag]
           [ec.util MersenneTwisterFast]
           [java.util Collection])) ; for a type hint below

;; Conventions:
;; * Var names containing atoms have "$" as a suffix.
;; * Adding an apostrophe to a var name, e.g. making x into x', means
;;   that this is the next value of that thing.  Sometimes this name will
;;   be reused repeatedly in a let as the value is sequentially updated.

;(use '[clojure.pprint]) ; DEBUG

(declare make-popenv next-popenv organism-setter 
         add-organism-to-rand-loc!
         move-snipes move-snipe!  choose-next-loc
         add-to-energy eat-if-appetizing snipes-eat snipes-die snipes-reproduce
         cull-snipes cull-snipes-to-max age-snipes excess-snipes snipes-in-env 
         obey-carrying-capacity add-snipes! add-snipes-to-min)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; TOP LEVEL FUNCTIONS

;; Note: The Env vs PopEnv distinction made sense in the original simulation
;; repo, pasta, from which this one is derived.  Here it does no work,
;; though, so I should merge the two defrecord classes.  However, that
;; involves refactoring or removing several functions here, in Gui.clj,
;; and perhaps elsewhere, so I haven't done it yet.

(defrecord Env [snipe-field])  ; snipe-field is an ObjectGrid2D

(defrecord PopEnv [env snipe-map curr-snipe-id$]) ; an env and a map from ids to snipes

(defn make-env
  "Returns new Env with snipes."
  [rng cfg-data$ curr-snipe-id$]
  (let [cfg-data @cfg-data$
        {:keys [env-width env-height]} cfg-data
        snipe-field (ObjectGrid2D. env-width env-height)]
    (.clear snipe-field)
    (add-snipes! rng cfg-data$ snipe-field (:num-snipes cfg-data) sn/make-rand-snipe curr-snipe-id$)
    (Env. snipe-field)))

(defn make-snipe-map
  "Make a map from snipe ids to snipes."
  [^ObjectGrid2D snipe-field] ; ^ObjectGrid2D east-snipe-field
  (into {} (map #(vector (:id %) %)) ; transducer w/ vector: may be slightly faster than alternatives
        (concat (.elements snipe-field)))) ; btw cost compared to not constructing a snipes map is trivial

(defn make-popenv
  [rng cfg-data$]
  (let [curr-snipe-id$ (atom 0)
        env (make-env rng cfg-data$ curr-snipe-id$)]
    (PopEnv. env 
             (make-snipe-map (:snipe-field env))
             curr-snipe-id$)))

(defn next-snipe-id
  [curr-snipe-id$]
  (swap! curr-snipe-id$ inc))

(defn die-move-spawn
  "In Example this only implements movement, but you could add birth and death.
  (See the pasta repo for an illustration of how to do that.)"
  [rng cfg-data$ env curr-snipe-id$]
  (let [cfg-data @cfg-data$
        {:keys [snipe-field ]} env
        snipe-field' (move-snipes rng cfg-data snipe-field)]     ; only the living get to move
    (Env. snipe-field')))

(defn next-popenv
  "Given an rng, a simConfigData atom, and a Env, return a new Env for
  the next time step.  Snipes eat, reproduce, die, and move."
  [popenv rng cfg-data$]
  (let [{:keys [env curr-snipe-id$]} popenv
        env' (die-move-spawn rng cfg-data$ (assoc env :snipe-field (:snipe-field env)) curr-snipe-id$)
        snipe-map' (make-snipe-map (:snipe-field env'))]
    (PopEnv. env' snipe-map' curr-snipe-id$)))

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
  If :left or :right is passed for env, only looks for locations in
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
 [rng cfg-data$ field num-to-add snipe-maker curr-snipe-id$]
 (let [cfg-data @cfg-data$
       {:keys [env-width env-height]} cfg-data]
  (dotimes [_ num-to-add] ; don't use lazy method--it may never be executed
   (add-organism-to-rand-loc! rng cfg-data field env-width env-height 
    (organism-setter (partial snipe-maker rng cfg-data$ (next-snipe-id curr-snipe-id$)))))))

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
