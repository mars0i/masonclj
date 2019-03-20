;; This software is copyright 2016, 2017, 2018, 2019 by Marshall Abrams, 
;; and is distributed under the Gnu General Public License version 3.0 
;; as specified in the the file LICENSE.

;(set! *warn-on-reflection* true)

(ns utils.map2csv
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.string :as st]))

(defn keyword-to-column-name
  "Create a column label string from keyword k."
  [k]
  (st/replace (name k) #"-" "_"))

(defn label-row
  "Returns sorted column label strings made from keys in map m."
  [m]
  (map keyword-to-column-name
       (sort (keys m))))

(defn data-row
  "Returns values from map m sorted to match column name order."
  [m]
  (vals (into (sorted-map) m)))

(defn spit-csv
  "Given a sequence of sequences of data, opens a file and writes to it
  using write-csv.  Options are those that can be passed to spit or writer."
  [f rows & options]
   (with-open [w (apply io/writer f options)] ; could use :quote? true but doesn't work?
     (csv/write-csv w rows)))

(defn spit-map
  "Writes map m to file f with keys formatted by label-row and values formatted
  by data-row.  Options are those that can be passed to spit or writer."
  [f m & options]
  (apply spit-csv f 
                   [(label-row m) (data-row m)]
                   options))

(defn spit-maps
  "Assumes maps ms have the same keys.  After writing a label row formatted with
  keys by label-row to file f, writes values from each map, one map per row 
  formatted by data-row. Options are those that can be passed to spit or writer."
  [f ms & options]
  (apply spit-csv f 
                   (cons (label-row (first ms))
                         (map data-row ms))
                   options))

(defn spit-map-seqs
  "mss is a sequence of sequences of maps, where the maps at the same index in
  each inner sequence have the same keys.  Labels formatted from keys by 
  label-row will be made into a single sequence, and each data row will be
  created from the values of the maps in each sequence in mss.  Options are 
  those that can be passed to spit or writer."
  [f mss & options]
  (apply spit-csv f 
                   (cons (mapcat label-row (first mss))
                         (mapcat data-row mss))
                   options))
