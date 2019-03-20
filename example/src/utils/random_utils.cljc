;;; This software is copyright 2013, 2014, 2015, 2016 by Marshall Abrams, and
;;; is distributed under the Gnu General Public License version 3.0 as
;;; specified in the file LICENSE.

;(set! *warn-on-reflection* true)

(ns utils.random-utils
  (:require [utils.random :as r])) ; different dependng on clj vs cljs

(defn make-rng-print-seed
  "Make a seed, print it to stdout, then pass it to make-rng."
  []
  (let [seed (r/make-long-seed)]
    (println seed)
    (r/make-rng seed)))

(defn truncate
  "Given an a function and arguments that generate random samples, returns a 
  random number generated with that function, but constrained to to lie within 
  [left,right].  Might not be particularly efficient--better for initialization
  than runtime?  Example: (truncate -1 1 next-gaussian rng 0 0.5)"
  [left right rand-fn & addl-args]
  (loop [candidate (apply rand-fn addl-args)]
    (if (and (>= candidate left) (<= candidate right))
      candidate
      (recur (apply rand-fn addl-args)))))

(defn sample-one
  "Given a non-empty collection, returns a single randomly-chosen element."
  [rng xs]
  (let [len (count xs)]
    (if (= len 1)
      (first xs)
      (nth xs 
           (r/rand-idx rng len)))))

;; lazy
;(def sample-with-repl sample-with-repl-3) ; see samplingtests2.xlsx
(defn sample-with-repl
  "Return num-samples from coll, sampled with replacement."
  [rng num-samples coll]
  (let [size (count coll)]
    (for [_ (range num-samples)]
      (nth coll (r/rand-idx rng size)))))

;; lazy if more than one sample
;; (deal with license issues)
; Derived from Incanter's algorithm from sample-uniform for sampling without replacement."
(defn sample-without-repl
  "Return num-samples from coll, sampled without replacement."
  [rng num-samples coll]
  (let [size (count coll)
        max-idx size]
    (cond
      (= num-samples 1) (list (nth coll (r/rand-idx rng size)))  ; if only one element needed, don't bother with the "with replacement" algorithm
      ;; Rather than creating subseqs of the original coll, we create a seq of indices below,
      ;; and then [in effect] map (partial nth coll) through the indices to get the samples that correspond to them.
      (< num-samples size) (map #(nth coll %) 
                                (loop [samp-indices [] indices-set #{}]    ; loop to create the set of indices
                                  (if (= (count samp-indices) num-samples) ; until we've collected the right number of indices
                                    samp-indices
                                    (let [i (r/rand-idx rng size)]             ; get a random index
                                      (if (contains? indices-set i)      ; if we've already seen that index,
                                        (recur samp-indices indices-set) ;  then try again
                                        (recur (conj samp-indices i) (conj indices-set i))))))) ; otherwise add it to our indices
      :else (throw 
              #?(:clj  (Exception. "num-samples can't be larger than (count coll).")
                 :cljs (js/Error.  "num-samples can't be larger than (count coll)."))))))


;; lazy
;; This version repeatedly calls nth coll with a new random index each time.
;(defn sample-with-repl-1
;  [rng num-samples coll]
;  (let [size (count coll)]
;    (repeatedly num-samples 
;                #(nth coll (r/rand-idx rng size)))))

;; lazy
;; This version is inspired by Incanter, which does it like this:
;;        (map #(nth x %) (sample-uniform size :min 0 :max max-idx :integers true))
;; You get a series of random ints between 0 and the coll size,
;; and then map nth coll through them.
;(defn sample-with-repl-2
;  [rng num-samples coll]
;  (let [size (count coll)]
;    (map #(nth coll %) 
;         (repeatedly num-samples #(r/rand-idx rng size)))))

;; not lazy
;(defn sample-with-repl-4
;  [rng num-samples coll]
;  (let [size (count coll)]
;    (loop [remaining num-samples result []] 
;      (if (> remaining 0)
;        (recur (dec remaining) (conj result 
;                                     (nth coll (r/rand-idx rng size))))
;        result))))
