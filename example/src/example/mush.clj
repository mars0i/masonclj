;; This software is copyright 2016, 2017, 2018, 2019 by Marshall Abrams, 
;; and is distributed under the Gnu General Public License version 3.0 
;; as specified in the the file LICENSE.

;(set! *warn-on-reflection* true)

(ns example.mush
  (:require [utils.random :as ran])
  (:gen-class                ; so it can be aot-compiled
    :name example.mush))  ; without :name other aot classes won't find it

;; Mushrooms don't have explicit ids.  They are generic
;; except for size and nutritional value.  (If you want to
;; look at both via the inspector functionality in the GUI,
;; they can be differentiated by internal Java id numbers.)

(defrecord Mush [size sd nutrition rng])

(defn make-mush [size sd nutrition rng]
  (Mush. size sd nutrition rng))

(defn appearance
  [mush]
  (let [{:keys [size sd rng]} mush]
    (ran/next-gaussian rng size sd))) ; size used as mean
