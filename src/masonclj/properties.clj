;; This software is copyright 2019 by Marshall Abrams, 
;; and is distributed under the Gnu Lesser General Public License version 3.0 
;; as specified in the the file LICENSE.

(ns masonclj.properties
  (:require [masonclj.utils :as u])
  (:import [sim.util Properties SimpleProperties Propertied]
           [sim.portrayal.simple CircledPortrayal2D]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; MAKE-PROPERTIES
;; Function that generates a MASON Properties object that can be used
;; to track an agent's identity over time, even though it's a
;; defrecord whose JVM identity keeps changing as it is updated.

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
  "make-properties returns a sim.util.Properties subclass that can be
  returned by the properties method implemented by agent defrecord for
  its sim.util.Propertied interface.  This can allow fields to be
  displayed in the GUI on request:  It will be used by the MASON GUI to
  allow inspectors to follow a functionally updated agent, i.e. one
  whose JVM identity may change over time.  (If an agent type is a
  defrecord but is never modified, or agents are objects that that
  retain pointer identity when modified, there's no need to implement
  the Propertied interface.) The curr-agent-slice argument should be a
  parameterless function that always returns the current time-slice of
  an agent.  (The function might be a closure over information in the
  original agent slice that will be passed to Propertied's properties
  method, and this information might be used to look up the current
  slice.  See the defagt source for illustration.) The fields argument
  consists of zero or more 3-element sequences in each of which the
  first element is a key for a field in the agent, the second is a Java
  type for that field, and the third is a string describing the field.
  If the defrecord that implements Propertied contains contains a field
  named circled$, which should contain an atom around a boolean, this
  will be used to track whether the agent is circled in the GUI."
  [curr-agt-slice & fields]
  (let [property-keys (vec (map data-field-key fields))
        circled$-idx (.indexOf property-keys :circled$) ; returns -1 if not found
        types (vec (map data-type fields))
        descriptions (vec (map data-description fields))
        num-properties (count property-keys)
        names (mapv name property-keys)
        are-writeable (vec (cons true (repeat num-properties false)))
        hidden        (vec (repeat num-properties false)) ; no properties specified here are to be hidden from GUI
        id (:id (curr-agt-slice))] ; If the original agent doesn't have a field named "id", this will be nil.
    ;; I don't want to require that there be a circled$ field--maybe you just don't care about this in the GUI.
    ;; And I don't want a cryptic exception to be thrown, nor to print a warning every time this is called.
    (when (>= circled$-idx 0) ; So we'll silently ignore absence of a circled$ field.
      (reset! (:circled$ (curr-agt-slice)) true)) ; this would fail if no circled$ field
    (proxy [Properties] [] ; the methods below are expected by Properties
           (getObject [] (curr-agt-slice))
           (getName [i] (names i))
           (getDescription [i] (descriptions i))
           (getType [i] (types i))
           (getValue [i]
             (let [v ((property-keys i) (curr-agt-slice))]
               (cond (u/atom? v) @v
                     (keyword? v) (name v)
                     :else v)))
           (setValue [i newval]      ; allows user to turn off circled in UI
             (when (= circled$-idx i)  ; If no circled$ field, this will simply never fire
               (reset! (:circled$ (curr-agt-slice))
                       (Boolean/valueOf newval)))) ; it's always a string that's returned from UI. (Do *not* use (Boolean. newval); it's always truthy in Clojure.)
           (isHidden [i] (hidden i))
           (isReadWrite [i] (are-writeable i))
           (isVolatile [] false)
           (numProperties [] num-properties)
           (toString [] (str "<SimpleProperties for agent " id ">")))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; DEFAGENT
;; Macro that that defines a defrecord and constructor/factory 
;; method that implements Propertied using make-properties, 
;; and adds a circled$ field.

;; Note make-curr-agent-slice is a function make and not a simple
;; function so that the result can be a closure over the first
;; time slice.
(defmacro defagt
  "defagt defines a defrecord type and a corresponding factory
  function: 1. defagt will define the defrecord type with the name
  given by the agent-type argument, and with field names specified in
  the fields argument (a sequence), plus an additional initial field
  named circled$.  This can be used to track whether an agent is circled
  in the GUI. 2. defagt defines a special factory function, named with
  the defrecord type name prefixed by '-->', that accepts  arguments for
  the fields specified in defagt's fields argument, passing them to
  the usual '->' factory function.  The '-->' factory function but will
  also initialize the circled$ field to (atom false), so by default the
  agent will not be circled in the gui. 3. In addition to any
  user-supplied defrecord options and specs specified in the
  addl-defrecord-args argument, the new defrecord will override
  java.lang.Object's toString method to incorporate an agent id if id is
  included in the fields argument, and will implement the MASON
  sim.util.Propertied interface. Propertied has one method, properties,
  which is passed the first time-slice of an agent and returns an
  instance of sim.util.Properties. The generated code does this by
  calling masonclj.properties/make-properties. defagt passes to
  make-properties the result of applying its make-curr-agent-slice
  argument to the first time-slice.  The make-curr-agent-slice function
  should return a 'current agent slice' function that can look up and
  return an agent's current time-slice using information in its first
  time-slice.  defagt also passes its gui-field-specs argument to
  make-properties.  See documentation on make-properties for more
  information on its parameters.  (Note that make-properties prefers
  that the defrecord have a circled$ field, which is why defagt adds
  circled$ to the new defrecord type.)"
  [agent-type fields make-curr-agent-slice gui-fields-specs
   & addl-defrecord-args]
  (let [clojure-constructor-sym# (symbol (str "->" agent-type))
        defagt-constructor-sym# (symbol (str "-->" agent-type))]
    `(do
       (defrecord ~agent-type [~'circled$ ~@fields]
         Propertied
         (properties [first-slice#]
           (make-properties (~make-curr-agent-slice first-slice#)
                            [:circled$ java.lang.Boolean
                             "Field that indicates whether agent is circled in GUI."]
                            ~@gui-fields-specs))
         Object
         (toString [obj#] (str "<" '~agent-type " " (:id obj#) ">")) ; will work even if id doesn't exist
         ~@addl-defrecord-args)
       (defn ~defagt-constructor-sym#
         [~@fields]
         (~clojure-constructor-sym# (atom false) ~@fields))))) ; (atom false) for circled$

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; MAKE-FNL-CIRCLED-PORTRAYAL
;; Function that returns a portrayal class that displays
;; a circle around an agent--or not--dependening on the value
;; of its circled$ field.

(defn make-fnl-circled-portrayal
  "Create a subclass of CircledPortrayal2D for agents with a
  circled$ field and that are composed of distinct time-slices" 
  [color child-portrayal]
  (proxy [CircledPortrayal2D] [child-portrayal color false]
    (draw [agt graphics info]
      (.setCircleShowing this @(:circled$ agt))
      (proxy-super draw agt graphics info))))
