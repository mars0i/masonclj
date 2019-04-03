;; This software is copyright 2019 by Marshall Abrams, 
;; and is distributed under the Gnu Lesser General Public License version 3.0 
;; as specified in the the file LICENSE.

(ns masonclj.properties
    (:require [masonclj.utils :as u])
    (:import [sim.util Properties SimpleProperties Propertied]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; MAKE-PROPERTIES
;; Function that generates a MASON Properties object that can be used
;; to track an agent's identity over time even though it's a
;; defrecord whose JVM identity keeps changing as it's updated.

;; More informative sequence accessor abbreviations:
(def data-field-key first)
(def data-type second)
(def data-description u/third)

;; Current version of next function does not allow any fields to be
;; modifiable from the GUI.  The code could be extended to allow this.
;; Code below makes use of the fact that in Clojure, vectors can be treated
;; as functions of indexes, returning the indexed item; that keywords such
;; as :x can be treated as functions of maps; and that defrecords such as
;; snipes can be treated as maps.
;; Q: Why is data accessed by indexes into vectors?
;; A: Because MASON passes indexes to Properties methods.
;; Q: Should the fields argument consiste of maps rather than vectors?
;; A: Maybe, but not in a verbose way.  Maybe if the field key were
;;    used as the key, and the other stuff was in a value.
(defn make-properties
  "Returns a Properties subclass for use by an agent defrecord's Propertied's
  properties method, so that certain fields can be displayed in the GUI on 
  request.  Used by the MASON GUI to allow inspectors to follow a functionally 
  updated agent, i.e. one whose JVM identity may change over time.  (If an
  agent type is a defrecord but is never modified, or agents are objects that
  that retain [pointer] identity when modified, then there's no need to
  implement the Propertied interface.)  
  The curr-agent-slice argument should be a parameterless function that
  will always return the current time-slice of the agent.  (It may be a
  closure over information in the original agent slice that will be
  passed to Propertied's properties method; this information might be
  used to look up the current slice.)
  The fields argument consists of zero or more 3-element sequences in
  each of which the first element is a key for a field in the agent, the
  second is a Java type for that field, and the third is a string
  describing the field.
  make-properties assumes that the defrecord returned by curr-agent-slice 
  contains a field named circled$ containing an atom around a boolean, which
  will track whether the agent is circled in the GUI."
  [curr-agent-slice & fields]
  (let [property-keys (vec (map data-field-key fields))
        circled$-idx (.indexOf property-keys :circled$) ; returns -1 if not found
        types (vec (map data-type fields))
        descriptions (vec (map data-description fields))
        ;; Shadow the first four parameters by adding circled$:
        num-properties (count property-keys)
        names (mapv name property-keys)
        are-writeable (vec (cons true (repeat num-properties false)))
        hidden        (vec (repeat num-properties false)) ; no properties specified here are to be hidden from GUI
        id (:id (curr-agent-slice))] ; If the original agent doesn't have a field named "id", this will be nil.
    (reset! (:circled$ (curr-agent-slice)) true) ; this will fail if no circled$ field
    (proxy [Properties] [] ; the methods below are expected by Properties
      (getObject [] (curr-agent-slice))
      (getName [i] (names i))
      (getDescription [i] (descriptions i))
      (getType [i] (types i))
      (getValue [i]
        (let [v ((property-keys i) (curr-agent-slice))]
          (cond (u/atom? v) @v
                (keyword? v) (name v)
                :else v)))
      (setValue [i newval]      ; allows user to turn off circled in UI
        (when (= circled$-idx i)  ; If no circled$ field, this will simply never fire
          (reset! (:circled$ (curr-agent-slice))
                  (Boolean/valueOf newval)))) ; it's always a string that's returned from UI. (Do *not* use (Boolean. newval); it's always truthy in Clojure.)
      (isHidden [i] (hidden i))
      (isReadWrite [i] (are-writeable i))
      (isVolatile [] false)
      (numProperties [] num-properties)
      (toString [] (str "<SimpleProperties for agent " id ">")))))

(defmacro defagent
  "INCOMPLETE: fields must include a field named id; this is used in the toString
  method for this agent.  make-curr-agent-slice will be passed the original
  time-slice of this agent, and should return a no-argument function that
  will always return the current time-slice of the agent.  A field named
  circled$ will be added as the first field; it should always be initialized
  with an atom of a boolean."
  [agent-type fields make-curr-agent-slice gui-fields-specs & addl-defrecord-args] ; function-maker and not function so it can capture id inside 
  (let [clojure-constructor-sym# (symbol (str "->" agent-type))
        defagent-constructor-sym# (symbol (str "-->" agent-type))]
    `(do
       (defrecord ~agent-type [~'circled$ ~@fields]
         Propertied
         (properties [original-snipe#]
           (make-properties (~make-curr-agent-slice original-snipe#)
                            [:circled$ java.lang.Boolean "Field that indicates whether agent is circled in GUI."]
                            ~@gui-fields-specs))
         Object
         (toString [obj#] (str "<" '~agent-type " " (:id obj#) ">")) ; will work even if id doesn't exist
         ~@addl-defrecord-args)
       (defn ~defagent-constructor-sym#
         [~@fields]
         (~clojure-constructor-sym# (atom false) ~@fields))))) ; (atom false) for circled$
