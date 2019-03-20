;; This software is copyright 2016, 2017, 2018, 2019 by Marshall Abrams, 
;; and is distributed under the Gnu General Public License version 3.0 
;; as specified in the the file LICENSE.

;(set! *warn-on-reflection* true)

(ns example.stats
  (:require [example.snipe :as sn]
            [utils.map2csv :as m2c]
            [clojure.data.csv :as csv]
            [clojure.pprint :as pp]
            [clojure.math.numeric-tower :as math]
            [com.rpl.specter :as s])
  (:import [sim.field.grid ObjectGrid2D]))

;; from https://clojuredocs.org/clojure.core/reduce-kv#example-57d1e9dae4b0709b524f04eb
;; Or consider using Specter's (transform MAP-VALS ...)
(defn map-kv
  "Given a map coll, returns a similar map with the same keys and the result 
  of applying f to each value."
  [f coll]
  (reduce-kv (fn [m k v] (assoc m k (f v)))
             (empty coll) coll))

(defn sorted-group-by
  [f coll]
  (into (sorted-map) (group-by f coll)))

(defn get-pop-size
  [cfg-data]
  (count (:snipe-map (:popenv cfg-data))))

(defn sum-snipes
  "Given a simple collection (not a map) of snipes, returns a map containing
  sums of values of snipes of different classes.  The sum is due to whatever 
  function f determiness about the snipes.  e.g. with no f argument, we just 
  increment the value to simply count the snipes in each class.  Keys are named after 
  snipe classes: :k-snipe, :r-snipe, :r-snipe, :s-snipe.
  An additional entry, :total, contains a total count of all snipes.  If there
  are additional collection arguments, the counts will be sums from all
  of the collections."
  ([snipes] (sum-snipes snipes (fn [v _] (inc v))))
  ([snipes f]
   (let [summer (fn [sum s]
                      (cond (sn/k-snipe? s) (update sum :k-snipe f s) ; the value of the field in sum will be the first arg to f, followed by s
                            (sn/s-snipe? s) (update sum :s-snipe f s)
                            (sn/r-snipe? s) (update sum :r-snipe f s)))]
     (reduce summer
             {:total (count snipes)
              :k-snipe 0 
              :s-snipe 0 
              :r-snipe 0}
             snipes)))
  ([snipes f & more-snipes]
   (apply merge-with +            ; overhead of map and apply should be minor relative to counting process
          (map #(sum-snipes % f)
               (cons snipes more-snipes))))) ; cons is really cheap here

(defn snipe-freqs
  "Given counts that result from sum-snipes, returns a map containing relative 
  frequencies of snipes of different classes, plus the total
  number of snipes examined.  Keys are named after snipe classes: :k-snipe, 
  :r-snipe, :r-snipe-, :s-snipe, plus :total.  Doesn't
  work with quantities other than counts."
  [counts]
  (let [total (:total counts)]
    (if (pos? total)
      (map-kv (fn [n] (double (/ n total))) counts)
      (map-kv (fn [_] 0) counts))))

(defn classify-by-snipe-class
  [snipe]
  (cond (sn/k-snipe? snipe) :k
        (sn/r-snipe? snipe) :r
        (sn/s-snipe? snipe) :s
        :else nil))

(defn classify-by-pref
  [snipe]
  (cond (pos? (:mush-pref snipe)) :pos
        (neg? (:mush-pref snipe)) :neg
        :else :zero))

(def group-by-snipe-class (partial sorted-group-by classify-by-snipe-class))
(def group-by-pref (partial sorted-group-by classify-by-pref))
(def group-by-subenv (partial sorted-group-by :subenv))

(defn classify-snipes
  "Returns a hierarchical map of maps of maps of colls of snipes in categories."
  [cfg-data]
   (let [popenv (:popenv cfg-data)
         snipes (concat (.elements ^ObjectGrid2D (:snipe-field (:west popenv)))
                        (.elements ^ObjectGrid2D (:snipe-field (:east popenv))))]
     (->> snipes
          (group-by-snipe-class)                                 ; creates a map by snipe class
          (s/transform s/MAP-VALS group-by-subenv)               ; replaces each coll of snipes by a map by subenv
          (s/transform [s/MAP-VALS s/MAP-VALS] group-by-pref)))) ; replaces each coll of snipes by a map by pos/neg mush-pref

(defn sum-by
  [k xs]
  (reduce (fn [sum x] (+ sum (k x)))
          0.0 xs))

(defn subpop-stats
  "Given a collection of snipes, returns a sequence of summary statistics:
  count, average energy, average mush preference, and average age."
  [snipes]
   (let [num-snipes (count snipes)
         avg-energy (if (pos? num-snipes) (/ (sum-by :energy snipes) num-snipes) 0) ; return 0 if there are no snipes
         avg-pref (/ (sum-by :mush-pref snipes) num-snipes)
         avg-age (/ (sum-by :age snipes) num-snipes)]
     [num-snipes avg-energy avg-pref avg-age]))
     ;{:count num-snipes :energy avg-energy :pref avg-pref :age avg-age}

(def csv-header ["run" "step" "snipe_class" "subenv" "pref_sign" "count" "energy" "pref" "age"])

;; leaf-seqs
;; Specter navigator operator that allows me to run snipe-stats on a classified snipes 
;; structure that includes a :step element.  I don't follow the ugly Specter convention 
;; of naming navigators with all-caps symbols.  This code based on example under "Recursive 
;; navigation" at http://nathanmarz.com/blog/clojures-missing-piece.html .
;; Note that while there is no primitive test for a non-map collection, because of the way
;; that MAP-VALS works, the code below only tests for coll? when we are no longer looking at 
;; a map; it functions as a test for all non-map collections at that point.
;; (You might think that this function could be replaced by (walker sequential?), but
;; walker walks into maps as if they were sequences, and then sees MapEntrys as sequences.
;; Maybe would work with some more complex predicate instead of sequential?, but then
;; you're doing that multiple-test on every node.  I think that the def I give below
;; is probably better.) 
(def ^{:doc "Specter navigator that recurses into recursively embedded maps of arbitrary 
  depth, operating only on non-map collection leaf values (including sets,
  despite the name of the navigator)."}
  leaf-seqs (s/recursive-path [] p
              (s/if-path map?       ; if you encounter a map
                 [s/MAP-VALS p]     ; then look at all of its vals, and the rest of the structure (i.e. p)
                 [s/STAY coll?])))  ; if it's not a map, but it's a coll, then return it to do stuff with it
                                    ; otherwise, just leave whatever you find there alone

;; note this:
;; (flatten (transform [(recursive-path [] p (if-path map? (continue-then-stay MAP-VALS p)))] first a))

(defn snipe-stats
  "Given a hierarchy of maps produced by classify-snipes (optionally
  with extra map entries such as one listing the step at which the
  data was collected), returns a map with the same structure but
  with leaf snipe collections replaced by maps of summary statistics
  produced by subpop-stats."
  [classified-snipes]
  (s/transform [leaf-seqs]  ; or e.g. [s/MAP-VALS s/MAP-VALS], but that's restricted to exactly two levels
                subpop-stats
                classified-snipes))

;; Based on an answer by miner49r at
;; http://stackoverflow.com/questions/21768802/how-can-i-get-the-nested-keys-of-a-map-in-clojure:
;; See oldcode.clj or commits af85d66 and 340c313 for alternative defs, and 
;; doc/notes/squarestatstimes.txt for speed comparison.  This version and a similar one
;; were about twice as fast as two others on representative stats data trees.
(defn leaves-to-row
  "Given an embedded map structure with sequences of per-category snipe summary
  statistics at the leaves, returns a collection of sequences with string versions
  of the map keys, representing category names, followed by the summary statistics.
  (This prepares the data for writing to a CSV file that can be easily read into
  an R dataframe for use by Lattice graphics.)"
  ([stats] (leaves-to-row [] stats))
  ([prev stats]                ; prev contains keys previously accumulated in one inner sequence
   (reduce-kv (fn [result k v] ; result accumulates the sequence of sequences
                (if (map? v)
                  (into result (leaves-to-row (conj prev (name k)) v)) ; if it's a map, recurse into val, adding key to prev
                  (conj result (concat (conj prev (name k)) v)))) ; otherwise add the most recent key and then add the inner data seq to result
              []    ; outer sequence starts empty
              stats)))

(defn make-stats-at-step
  [cfg-data seed step]
  {:run seed
   :step step
   :stats (snipe-stats (classify-snipes cfg-data))})

(defn leaves-to-row-at-step-for-csv
  [stats-at-step]
  (let [run (:run stats-at-step)
        step (:step stats-at-step)
        stats-row (leaves-to-row (:stats stats-at-step))]
    (map #(into [run step] %) stats-row)))

;; Illustration of usage for old version: 
;;
;;    user=> (def s804 (square-stats-at-step-for-csv (make-stats-at-step @data$ (.schedule cfg))))
;;    #'user/s804
;;    user=> (def s911 (square-stats-at-step-for-csv (make-stats-at-step @data$ (.schedule cfg))))
;;    #'user/s911
;;    user=> (pprint (cons csv-header (concat s804 s911)))
;;    (["step" "snipe_class" "subenv" "pref_sign" "count" "energy" "pref" "age"]
;;     (804 "k" "east" "pos" 23 11.26086956521739 2.7933525400119586E-4 600.0869565217391)
;;     (804 "k" "west" "neg" 39 13.35897435897436 -2.194019839094257E-4 581.9230769230769)
;;     (804 "r" "east" "neg" 25 7.24 -1.0 263.48)
;;     (804 "r" "east" "pos" 35 14.714285714285714 1.0 444.34285714285716)
;;     (804 "r" "west" "neg" 37 15.08108108108108 -1.0 466.8918918918919)
;;     (804 "r" "west" "pos" 24 7.333333333333333 1.0 523.625)
;;     (804 "s" "east" "neg" 20 6.55 -0.650021630731745 345.55)
;;     (804 "s" "east" "pos" 21 13.904761904761905 0.9048001299409795 484.57142857142856)
;;     (804 "s" "east" "zero" 1 10.0 0.0 4.0)
;;     (804 "s" "west" "neg" 47 14.297872340425531 -0.7447054437863198 451.72340425531917)
;;     (804 "s" "west" "pos" 12 7.583333333333333 0.916669501580321 316.1666666666667)
;;     (804 "s" "west" "zero" 1 10.0 0.0 14.0)
;;     (911 "k" "east" "pos" 28 10.714285714285714 2.7141106574043447E-4 593.1428571428571)
;;     (911 "k" "west" "neg" 41 13.121951219512194 -2.481524592827564E-4 648.3658536585366)
;;     (911 "k" "west" "pos" 2 12.5 5.415420603792033E-6 218.0)
;;     (911 "k" "west" "zero" 1 10.0 0.0 31.0)
;;     (911 "r" "east" "neg" 31 6.967741935483871 -1.0 230.32258064516128)
;;     (911 "r" "east" "pos" 39 15.256410256410257 1.0 500.79487179487177)
;;     (911 "r" "west" "neg" 46 14.826086956521738 -1.0 471.3695652173913)
;;     (911 "r" "west" "pos" 25 7.12 1.0 487.68)
;;     (911 "s" "east" "neg" 24 6.416666666666667 -0.6667013107370153 356.9583333333333)
;;     (911 "s" "east" "pos" 26 14.615384615384615 0.8846628654367455 487.7307692307692)
;;     (911 "s" "east" "zero" 1 10.0 0.0 6.0)
;;     (911 "s" "west" "neg" 52 14.576923076923077 -0.7692529972684045 511.7307692307692)
;;     (911 "s" "west" "pos" 15 7.266666666666667 0.866699845811923 288.53333333333336)
;;     (911 "s" "west" "zero" 3 10.0 0.0 9.0))

(defn write-stats-to-csv
  "Given a SimState cfg and a cfg-data, get current statistics and write to file in a format
  useful for reading into an R datagrame."
  [cfg-data seed step]
  (csv/write-csv (:csv-writer cfg-data)
                 (leaves-to-row-at-step-for-csv
                   (make-stats-at-step cfg-data seed step))))


(defn write-stats-to-console
  "Report summary statistics to standard output."
  [cfg-data step] 
  (pp/pprint (make-stats-at-step cfg-data 0 step)) ; 0 is dummy seed
  (println))

(defn report-stats
  [cfg-data seed step] 
  (if (:write-csv cfg-data)
    (write-stats-to-csv cfg-data seed step)
    (write-stats-to-console cfg-data step)))

(defn write-params-to-console
  "Print parameters in cfg-data to standard output."
  [cfg-data]
  (let [kys (sort (keys cfg-data))]
    (print "Parameters: ")
    (println (map #(str (name %) "=" (% cfg-data)) kys))))

(defn write-params-to-file
  "Write parameters to a csv file.  Note that if there's a csv basename,
  it will simply overwrite a file with the same name."
  ([cfg-data] (write-params-to-file cfg-data 
                                    (str (or (:csv-basename cfg-data) 
					     (str "pasta" (:seed cfg-data)))
					 "_params.csv")))
  ([cfg-data f] (m2c/spit-map f cfg-data)))

;(defn report-params
;  [cfg-data]
;  (if (:write-csv cfg-data)
;    (write-params-to-file cfg-data)
;    (write-params-to-console cfg-data)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; USED BY GUI INSPECTORS DEFINED IN example.simconfig

;; These functions provide a crude way to allow plotting and displaying
;; relative frequencies of snipes in the overall pop within the GUI.  
;; It should not be used with -parallel.  If there's a reason to do
;; that, this code needs to be modified.  However, you shouldn't need
;; to do that, because the csv-writing methods above allow you to
;; store data from which you can easily calculate the relative frequencies
;; at any point in time for which you record the data (using the
;; --report-every commandline option).

;; This variable is global to the entire application.  
;; That's OK as long as it's only used in the GUI.
(def freqs-for-gui$ (atom {}))

(defn get-freq-for-gui
  "Given an integer tick representing a MASON step, and a key k
  for a snipes class (:k-snipe, :r-snipe, :r-snipe,
  :s-snipe) or :total, returns the relative frequency of that snipe class
  in the current population in popenv, or the total number of snipes if
  :total is passed.  Note that data from previous ticks isn't kept.
  tick is just used to determine whether the requested data is from the
  same timestep as the last time that get-freq was called.  If not, then
  all of the frequencies are recalculated from the current population,
  and are associated with the newly passed tick, whether it's actually the 
  current tick or not."
  [tick k popenv]
  (let [freqs (or (@freqs-for-gui$ tick) ; if already got freqs for this tick, use 'em; else make 'em:
                  (let [{:keys [west east]} popenv
                        snipes (.elements ^ObjectGrid2D (:snipe-field west))
                        _ (.addAll snipes (.elements ^ObjectGrid2D (:snipe-field east)))
                        new-freqs (snipe-freqs (sum-snipes snipes))]
                    (reset! freqs-for-gui$ {tick new-freqs})
                    new-freqs))]
    (k freqs)))

(defn maybe-get-freq-for-gui
  "Kludge: Calls get-freq if and only if at timestep 1 or later.  Avoids
  irrelevant NPEs during initial setup."
  [tick k popenv]
  (if (and tick (pos? tick))
    (get-freq-for-gui tick k popenv)
    0.0))
