;; This software is copyright 2016, 2017, 2018, 2019 by Marshall Abrams, 
;; and is distributed under the Gnu General Public License version 3.0 
;; as specified in the the file LICENSE.

;(set! *warn-on-reflection* true)

(ns example.perception
  (:require [clojure.algo.generic.math-functions :as amath]
            [example.mush :as mu]
            [utils.random :as ran]
            [utils.random-utils :as ranu])
  (:import [sim.field.grid Grid2D ObjectGrid2D]
           [sim.util Bag]))

(declare pref-noise calc-k-pref k-snipe-pref r-snipe-pref subenv-loc-neighbors
         subenv-snipe-neighbors this-subenv-snipe-neighbors both-subenvs-snipe-neighbors
         best-neighbor get-best-neighbor-pref s-snipe-pref random-eat-snipe-pref 
         always-eat-snipe-pref)

;; Put this somewhere else? e.g. in cfg-data$ so can be set by the user?
(def pref-dt 0.00001)

(defn pref-noise [rng sd]
  (ran/next-gaussian rng 0 sd))

;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; K-SNIPE PREFERENCES

;; Simple algorithm for k-snipes that's supposed to:
;; a. learn what size the nutritious mushrooms are around here, and
;; b. then tend to eat only those, and not the poisonous ones
;; k-snipes learn.
;; See free-fn.nt9 for motivation, analysis, derivations, etc.
;; (mush-pref is called 'eat' there.)
(defn k-snipe-pref
  "Decides whether snipe eats mush, and updates the snipe's mush-pref in 
  response to the experience if so, returning a possibly updated snipe 
  along with a boolean indicating whether snipe is eating.  (Note that 
  the energy transfer resulting from eating will occur elsewhere, in 
  response to the boolean returned here.)"
  [rng snipe mush]
  (let [{:keys [mush-pref cfg-data$]} snipe
        {:keys [mush-mid-size k-pref-noise-sd]} @cfg-data$
        scaled-appearance (- (mu/appearance mush) mush-mid-size)
        eat? (pos? (* (+ mush-pref (pref-noise rng k-pref-noise-sd)) ; my effective mushroom preference is noisy. (even if starts at zero, I might eat.)
                      scaled-appearance))]           ; eat if scaled appearance has same sign as mush-pref with noise
    [(if eat?
       (assoc snipe :mush-pref (calc-k-pref rng snipe mush scaled-appearance)) ; if we're eating, this affects future preferences
       snipe)
     eat?]))

(defn calc-k-pref
  "Calculate a new mush-pref for a k-snipe.  Calculates an incremental change
  in mush-pref, and then adds the increment to mush-pref.  The core idea of the 
  increment calculation is that if the (somewhat random) appearance of a
  mushroom has a larger (smaller) value than the midpoint between the two 
  actual mushroom sizes, and the mushroom's nutrition turns out to be positive,
  then that's a reason to think that larger (smaller) mushrooms are nutritious,
  and the opposite for negative nutritional values.  Thus it makes sense to
  calculate the increment as a scaled value of the product of the mushroom's 
  nutrition and the difference between the appearance and the midpoint.  Thus
  positive values of mush-pref mean that in the past large mushrooms have often
  be nutritious, while negative values mean that small mushrooms have more
  often been nutritious, on average."
  [rng snipe mush scaled-appearance]
  (let [{:keys [nutrition]} mush
        {:keys [mush-pref cfg-data$]} snipe
        {:keys [mush-mid-size mush-size-scale]} @cfg-data$
        pref-inc (* pref-dt
                   nutrition
                   scaled-appearance
                   mush-size-scale)]
    (+ mush-pref pref-inc)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; R-SNIPE PREFERENCES

(defn r-snipe-pref
 "Always prefers size initially specified in its mush-pref field."
  [rng snipe mush]
  (let [{:keys [mush-pref cfg-data$]} snipe
        {:keys [mush-mid-size]} @cfg-data$
        scaled-appearance (- (mu/appearance mush) mush-mid-size)
        eat? (pos? (* mush-pref scaled-appearance))]  ; eat if scaled appearance has same sign as mush-pref
    [snipe eat?]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; S-SNIPE PREFERENCES (which depend on the r-snipe prefence function)

(defn s-snipe-pref
  "Like r-snipe-pref (which is called by this function), except that this
  function tries to set the snipe's mush-pref preference value by copying
  it from one of those neighbors that have the most energy.  This will 
  occur every time this function is called as long as the snipe still has 
  its initial mush-pref of 0.0.  Once it acquires a different preference,
  it will retain that preference for the rest of its life.  (Thus s-snipes
  are like r-snipes, except that while r-snipe preferences are the result
  of an initial random developmental process, s-snipe preferences are
  acquired through early social learning.)  Neighbors include all snipes
  within neighbor-radius of this snipe's location, *in both subenvs*: 
  The subenvs are thus treated as spatially identical but functionally 
  different domains of interest.  Snipes in one domain are obligate 
  specialists in the mushrooms of their own domain--their \"subenv\", but 
  can't tell whether other snipes are also specialists in the same domain."
  [rng snipe mush]
  (if (= 0.0 (:mush-pref snipe))
    (r-snipe-pref rng (assoc snipe :mush-pref (get-best-neighbor-pref rng snipe)) ; r-snipe-pref will pass back a snipe with this updated preference
                  mush)
    (r-snipe-pref rng snipe mush)))

(defn get-best-neighbor-pref
  "Get the preference of the best neighbor in both subenvs."
  [rng snipe]
  (:mush-pref 
    (best-neighbor rng (both-subenvs-snipe-neighbors snipe))))

(defn best-neighbor
  "Return the neighbor (or self) with the most energy.  If there's a tie, return
  a randomly chosen one of the best.  Assumes that there is at least one \"neighbor\":
  oneself."
  [rng neighbors]
  ;(println (map #(vector (:energy %) (:mush-pref %)) neighbors) "\n")(flush) ; DEBUG
  (ranu/sample-one rng 
                   (reduce (fn [best-neighbors neighbor]
                             (let [best-energy (:energy (first best-neighbors))
                                   neighbor-energy (:energy neighbor)]
                               (cond (< neighbor-energy best-energy) best-neighbors
                                     (> neighbor-energy best-energy) [neighbor]
                                     :else (conj best-neighbors neighbor))))
                           [(first neighbors)] (next neighbors)))) ; neighbors should always at least include the "student" snipe

(defn both-subenvs-snipe-neighbors
  [snipe]
  "Returns a MASON sim.util.Bag containing all snipes in the hexagonal region 
  around snipe's location in both of the subenvs, to a distance of neighbor-radius.  
  This will include the original snipe."
  (let [^Bag neighbors (subenv-snipe-neighbors :west snipe)]
    (.addAll neighbors ^Bag (subenv-snipe-neighbors :east snipe))
    neighbors))

(defn this-subenv-snipe-neighbors
  "Returns a MASON sim.util.Bag containing all snipes in the hexagonal region 
  around snipe's location in its subenv, to a distance of neighbor-radius.  
  This will include the original snipe."
  [snipe]
  (subenv-snipe-neighbors (:subenv snipe) snipe))

(defn subenv-snipe-neighbors
  "Returns a MASON sim.util.Bag containing all snipes in the hexagonal region 
  around snipe's location in the subenv corresponding to subenv, to a 
  distance of neighbor-radius.  This may include the original snipe."
  [subenv snipe]
  (let [{:keys [x y cfg-data$]} snipe]
    (subenv-loc-neighbors @cfg-data$ subenv x y)))

(defn subenv-loc-neighbors
  "Returns a MASON sim.util.Bag containing all snipes in the hexagonal region 
  around location <x,y> in the subenv corresponding to subenv, to a 
  distance of neighbor-radius.  This may include the snipe at <x,y>."
  [cfg-data subenv x y]
  (let[{:keys [popenv neighbor-radius]} cfg-data
        ^ObjectGrid2D snipe-field (:snipe-field (subenv popenv))]
    (.getHexagonalNeighbors snipe-field x y neighbor-radius Grid2D/TOROIDAL true)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; TRIVIAL PREFERENCES (for testing)

(defn always-eat-snipe-pref
 "Always eats."
  [rng snipe mush]
  [snipe true])

; needs revision since pref-noise now takes an sd parameter
; maybe don't use pref-noise
;(defn random-eat-snipe-pref
; "Decides by a coin toss whether to eat."
;  [rng snipe mush]
;  [snipe (pos? (pref-noise rng))])
