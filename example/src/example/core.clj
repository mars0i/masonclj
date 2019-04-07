;; This software is copyright 2016, 2017, 2018, 2019 by Marshall Abrams, 
;; and is distributed under the Gnu General Public License version 3.0 
;; as specified in the the file LICENSE.

;(set! *warn-on-reflection* true)

(ns example.core
  (:require [example.Sim :as sim]
            [example.GUI :as gui])
  (:gen-class))

(defn -main
  "This function, core/main, examines the use-gui command line option, if
  it exists, to decide whether to run the main in Sim.clj or the main in
  Example.clj (which will eventually run code in Sim)."
  [& args]
  ;; The Sim isn't available yet, so store commandline args for later access by start():
  (sim/record-commandline-args! args) ; defined by defsim: records args in commandline$, defined above
  (if (and args (not (:use-gui (:options @sim/commandline$)))) ; if commandline options, default to no-gui unless use-gui is true
    (sim/mein args)
    (gui/mein args))) ; otherwise default to gui
