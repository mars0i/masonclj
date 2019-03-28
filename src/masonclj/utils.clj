;; This software is copyright 2016, 2017, 2018, 2019 by Marshall Abrams, 
;; and is distributed under the Gnu Lesser General Public License version 3.0 
;; as specified in the the file LICENSE.

;; miscellaneous utility definitions for masonclj

(ns masonclj.utils)


(defn atom? [x] (instance? clojure.lang.Atom x)) ; This is unlikely to become part of clojure.core: http://dev.clojure.org/jira/browse/CLJ-1298


(defn current-directory
  "Returns the current working directory."
  []
  (.getCanonicalPath (clojure.java.io/file ".")))

;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Positional functions
;; clojure.core's first and second return nil if xs is too short (because
;; they're defined using next), while nth throws an exception in that case.  
;; For the principle of least surprise, and since count is O(1) in most 
;; cases (and all cases here), I'm reproducing the nil-if-too-short 
;; functionality in the defs below.  Also, I sometimes positively want this 
;; behavior.

(defn third 
  "Returns the third element of xs or nil if xs is too short."
  [xs] 
  (if (>= (count xs) 3)
    (nth xs 2)
    nil))

(defn fourth 
  "Returns the fourth element of xs or nil if xs is too short."
  [xs] 
  (if (>= (count xs) 4)
    (nth xs 3)
    nil))

(defn fifth
  "Returns the fifth element of xs or nil if xs is too short."
  [xs] 
  (if (>= (count xs) 5)
    (nth xs 4)
    nil))
