;; This software is copyright 2016, 2017, 2018, 2019 by Marshall Abrams, 
;; and is distributed under the Gnu Lesser General Public License version 3.0 
;; as specified in the the file LICENSE.

;; Defines a defparams macro that will define a subclass of MASON's
;; SimState with associated instance state variable, accessors, etc.

;; NOTE this will not work unless project.clj specifies that Sim
;; is aot-compiled.  e.g. if your overarching namespace path is named
;; "myproject", you need a line like this in project.clj:
;;     :aot [myproject.Sim]
;; or like this:
;;     :aot [myproject.Sim myproject.UI]

(ns masonclj.params
  (:require [clojure.string :as s]
            [masonclj.utils :as u]))

(def sim-class-sym 'Sim)
(def data-class-sym 'data)
(def data-rec-sym 'SimData)
(def data-rec-constructor '->SimData)
(def data-field-sym 'simData)
(def data-accessor '.simData)
(def init-genclass-sym 'init-sim-data)
(def init-defn-sym '-init-sim-data)
(def gui-vars-html-filename "gui_vars_table.html") ; will contain html for documentation of vars in GUI


;; Positional function abbreviations for accessing components 
;; of the fields argument below:
(def field-sym  first)
(def field-init second)
(def field-type u/third)
(def field-ui?  u/fourth)
(def field-description (comp second u/fifth))

(defn get-class-prefix
  "Given a Java/Clojure class identifier symbol or string, or class object (found
  e.g. in *ns*), returns a string containing only the path part before the last 
  period, stripping off the class name at the end."
  [class-rep]
  (s/join "." (butlast 
                (s/split (str class-rep) #"\."))))

(defn snake-to-camel-str
  "Converts a hyphenated string into the corresponding camel caps string."
  [string]
  (let [parts (s/split string #"-")]
    (reduce str (map s/capitalize parts))))

(defn snake-sym-to-camel-str
  "Convience wrapper for snake-to-camel-str that converts symbol to
  string before calling it."
  [sym]
  (snake-to-camel-str (name sym)))

(defn prefix-sym
  "Given a prefix string and a Clojure symbol, returns a Java 
  Bean-style accessor symbol using the prefix.  e.g.:
  (prefix-sym \"get\" this-and-that) ;=> getThisAndThat"
  [prefix stub-str]
  (symbol (str prefix stub-str)))

(defn make-accessor-sigs
  [get-syms set-syms classes]
  (mapcat (fn [get-sym set-sym cls] [[get-sym [] cls] [set-sym [cls] 'void]])
               get-syms set-syms classes))

(defn get-ui-fields
  "Given a fields argument to defparams, return a sequence containing 
  only those field specifications suitable for modification in the UI.
  These are those have a truthy fourth element"
  [fields]
  (filter field-ui? fields)) ; i.e. 

(defn get-range-fields
  "Given a fields argument to defparams, return a sequence containing 
  only those field specifications that include specification of the default
  range of values for the field--i.e. those field specs that have a sequence,
  presumably with exactly two elements, as the fourth element."
  [fields]
  (filter (comp sequential? field-ui?) fields))

(defn make-cli-spec
  "If a partial cli specification vector is present as the fifth element
  of field, returns a cli specification vector completed by inserting
  the long-option string as after the initial short-option string.  The
  rest of the partial cli specification vector should contain a description
  and any other keyword-based arguments allowed by clojure.tools.cli/parse-opts.
  The constructed long-option string will have the form 
  \"--key-sym <val-type>\"."
  [field]
  (let [[key-sym init val-type _ [short-opt & rest-of-cli-spec]] field]
    (when short-opt
      (into [short-opt 
             (str "--" key-sym 
                  (when (not= val-type 'boolean)          ; for booleans, don't require an argument
                    (str " <" val-type "> (" init ")")))]
            rest-of-cli-spec))))

(defn get-cli-specs
  [fields]
  (filter identity (map make-cli-spec fields)))

(defn make-commandline-processing-defs
  "If there any element of fields includes a fifth element, i.e. command-line 
  specification, generate commandline processing code; otherwise return nil."
  [fields]
  (when (some #(> (count %) 4) fields)
    `((defn ~'record-commandline-args!
        "Temporarily store values of parameters passed on the command line."
        [args#]
        ;; These options should not conflict with MASON's.  Example: If "-h" is the single-char help option, doLoop will never see "-help" (although "-t n" doesn't conflict with "-time") (??).
        (let [~'cli-options [["-?" "--help" "Print this help message."] ~@(get-cli-specs fields)]
              usage-fmt# (fn [~'options]
                           (let [~'fmt-line (fn [[~'short-opt ~'long-opt ~'desc]] (str ~'short-opt ", " ~'long-opt ": " ~'desc))]
                             (clojure.string/join "\n" (concat (map ~'fmt-line ~'options)))))
              {:keys [~'options ~'arguments ~'errors ~'summary] :as ~'cmdline} (clojure.tools.cli/parse-opts args# ~'cli-options)]
          (when ~'errors
            (run! println ~'errors)
            (println "MASON options should appear at the end of the command line after '--'.")
            (System/exit 1))
          (reset! ~'commandline$ ~'cmdline) ; commandline should be defined previously in Sim
          (when (:help ~'options)
            (println "Command line options (defaults in parentheses):")
            (println (usage-fmt# ~'cli-options))
            (println "MASON options can also be used after these options and after '--'.")
	    (println "For example, you can use -for to stop after a specific number of steps.")
            (println "-help (note single dash): Print help message for MASON.")
            (System/exit 0)))))))

(defn make-gui-vars-html
  "Given a sequence of Java variable name strings and descriptions of them
  formats the names and descriptions into an HTML table that can be inserted,
  for example, into the app's index.html or a README.md file."
  [java-var-names descriptions]
  (apply str
         (conj (vec (cons "<table style=\"width:100%\">"
                          (map (fn [v d]
                                 (format "<tr><td valign=top>%s:</td> <td>%s</td></tr>\n" v d))
                               java-var-names
                               descriptions)))
               "</table>")))

;; Maybe some of gensym pound signs are overkill. Can't hurt?
(defmacro defparams
  "defparams generates Java-bean style and other MASON-style accessors; a gen-class 
  expression in which their signatures are defined along with an instance 
  variable containing a Clojure map for their corresponding values; an 
  initializer function for the map; and a call to clojure.tools.cli/parse-opts
  to define corresponding commandline options.  fields is a sequence of 4- or 
  5-element sequences starting with names of fields in which configuration 
  data will be stored and accessed, followed by initial values and Java 
  type identifiers for the field.  The fourth element is either false to 
  indicate that the field should not be configurable from the UI, or truthy
  if it is.  In the latter case, it may be a two-element sequence containing 
  default min and max values to be used for sliders in the UI.  (This range 
  doesn't constrain fields' values in any other respect.) The fifth element,
  if present, specifies short commandline option lists for use by parse-opts,
  except that the second, long option specifier should be left out; it will be 
  generated from the parameter name.  The following gen-class options will
  automatically be provided in the macroexpansion of defparams: :state, 
  :exposes-methods, :init, :main, :methods.  Additional options can be provided
  in addl-gen-class-opts by alternating gen-class option keywords with their
  intended values.  If addl-gen-class-opts includes :exposes-methods or 
  :methods, the value(s) will be combined with automatically generated values
  for these gen-class options.  Note: defparams must be used only in a namespace
  named <namespace prefix>.Sim, where <namespace prefix> is the path before the
  last dot of the current namespace.  Sim must be aot-compiled in order for 
  gen-class to work.  When run, this macro also generates a file named
  gui_vars_table.html containing documentation on generated Java vars that will
  be manipulable from within the GUI.  That file can then be included into other
  documentation such as the index.html file displayed in the app."
  [fields & addl-gen-class-opts]
   (let [addl-opts-map (apply hash-map addl-gen-class-opts)
         field-syms# (map field-sym fields)   ; symbols for data object fields (?)
         field-inits# (map field-init fields) ; data field initial values (?)
         ui-fields# (get-ui-fields fields)    ; names of fields in GUI (?)
         ui-field-syms# (map field-sym ui-fields#) ; sybmols for fields in GUI (?)
         ui-field-descriptions# (map field-description ui-fields#)
         ui-field-types# (map field-type ui-fields#)
         ui-field-keywords# (map keyword ui-field-syms#)
         accessor-stubs# (map snake-sym-to-camel-str ui-field-syms#)
         get-syms#  (map (partial prefix-sym "get") accessor-stubs#) ; bean getter symbols
         set-syms#  (map (partial prefix-sym "set") accessor-stubs#) ; bean setter symbols
         -get-syms# (map (partial prefix-sym "-") get-syms#)         ; getter symbols with "-" prefix
         -set-syms# (map (partial prefix-sym "-") set-syms#)         ; setter symbols with "-" prefix
         range-fields# (get-range-fields ui-fields#)
         dom-syms#  (map (comp (partial prefix-sym "dom") snake-sym-to-camel-str first) ; dom range special MASON "bean" symbols
                        range-fields#)
         -dom-syms# (map (partial prefix-sym "-") dom-syms#) ; dom range symbols with "-" prefix
         dom-keywords# (map keyword dom-syms#)
         ranges# (map field-ui? range-fields#)
         gui-vars-html# (make-gui-vars-html accessor-stubs# ui-field-descriptions#)
         class-prefix (get-class-prefix *ns*)
         qualified-sim-class# (symbol (str class-prefix "." sim-class-sym))
         qualified-data-class# (symbol (str class-prefix "." data-class-sym))
         qualified-data-rec# (symbol (str class-prefix "." data-rec-sym))
         qualified-data-rec-constructor# (symbol (str class-prefix "." data-class-sym "/" data-rec-constructor))
         gen-class-opts# {:name qualified-sim-class#
                         :extends 'sim.engine.SimState
                         :state data-field-sym ; Experiment: :state (vary-meta data-field-sym assoc :tag qualified-data-rec#)
                         :exposes-methods (into '{start superStart} (:exposes-methods addl-opts-map))
                         :init init-genclass-sym
                         :main true
                         :methods (vec (concat (make-accessor-sigs get-syms# set-syms# ui-field-types#)
                                               (map #(vector % [] 'java.lang.Object) dom-syms#)
                                               (:methods addl-opts-map)))} 
         gen-class-opts# (into gen-class-opts# (dissoc addl-opts-map :exposes-methods :methods))
         this# (vary-meta 'this assoc :tag qualified-sim-class#)] ; add type hint to Sim arg of bean accessors to avoid reflection
         ;; Note re type-hinting the newval param of the setters below, see WhatIsThisBranch.md 
         ;; in branch type-hinted-newval in the pasta repo.

     ;; GENERATE HTML TABLE DOCUMENTING VARIABLES POSSIBLY VISIBLE IN GUI
     ;; Note this will only happen whem Sim.clj is recompiled.
     (println "Writing GUI vars html table to file" gui-vars-html-filename ".")
     ;; Write the html file to the current directory:
     (spit (str (u/current-directory) "/" gui-vars-html-filename) ; DOES THIS WORK IN WINDOWS?
           gui-vars-html#)

     ;; GENERATE CODE FOR Sim.clj:
     `(do
        ;; Put following in its own namespace so that other namespaces can access it without cyclicly referencing Sim:
        ;; DEFINE CONFIG DATA RECORD:
        (ns ~qualified-data-class#)
        (defrecord ~data-rec-sym ~(vec field-syms#))

        ;; The rest is in the main config namespace:
        ;; DEFINE SIM CONFIG CLASS:
        (ns ~qualified-sim-class# 
          (:require [~qualified-data-class#])
          (:import ~qualified-sim-class#)
          (:gen-class ~@(apply concat gen-class-opts#)))  ; NOTE qualified-data-rec must be aot-compiled, or you'll get class not found errors.

        ;; FUNCTION THAT INITIALIZES DATA RECORD STORED IN SIM CONFIG CLASS:
        (defn ~init-defn-sym [~'seed] [[~'seed] (atom (~qualified-data-rec-constructor# ~@field-inits#))])

        ;; DEFINE BEAN AND OTHER ACCESSORS FOR MASON UI:
        ~@(map (fn [sym# keyw#] (list 'defn sym# (vector this#) `(~keyw# @(~data-accessor ~'this))))
               -get-syms# ui-field-keywords#)
        ~@(map (fn [sym# keyw#] (list 'defn sym# (vector this# 'newval) `(swap! (~data-accessor ~'this) assoc ~keyw# ~'newval)))
               -set-syms# ui-field-keywords#)
        ~@(map (fn [sym# keyw# range-pair#] (list 'defn sym# (vector this#) `(Interval. ~@range-pair#)))
               -dom-syms# dom-keywords# ranges#)

        ;; DEFINE COMMANDLINE OPTIONS:
        ~@(make-commandline-processing-defs fields)
      )))
