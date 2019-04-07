masonclj documentation
====
Marshall Abrams

## Overview

masonclj provides two kinds functions and macros for two purposes:

* Making easier to define parameters for control of a model via the
  GUI and the command line.
* Making it easier to track agents using the MASON inspector
  facilities if you use defrecords to implement agents.

(See the doc/general directory for notes that provide rationales for
some of the design choices assumed by masonclj.)

### Model parameters coordination

See [params.md](https://github.com/mars0i/masonclj/blob/master/doc/masonclj/params.md)
for documentation on the `defparams` macro in the `masonclj.params`
package.  `defparams` does two things:

1. Generates a series of coordinated definitions for model
parameters.  It's useful to include a series of related definitions
for model parameters.  In Clojure, many of would naturally be put in
diffent locations in your code, and in some cases they must be put
in different places.  `defparams` generates these definitions
all at once, making it easier to keep them coordinated.

2. Moves global configuration data into its own namespace.  There
are some situations in which it's useful to put global
configuration data in a separate namespace to avoid cyclic
dependencies (which Clojure doesn't always allow).  The global
configuration data is used by the model parameter definitions.

### Tracking agents in the GUI

See
[properties.md](https://github.com/mars0i/masonclj/blob/master/doc/masonclj/properties.md)
for documentation on the `defagent` macro and the `make-properties`
function in the `masonclj.properties`.  These allow you to use
functionally updated defrecord objects as MASON agents while still
making it possible to track an agent in the MASON GUI using MASON's
inspector functionality.

## Notes on variable naming

General naming convention: I sometimes use CamelCase to name things
that have a Java-ey role with MASON, but mostly use Clojure-standard
kebab-case.

Variables containing Clojure atoms: I name variables that contain atoms
with "$" at the end of their names. (This is nonstandard; the norm in
Clojure is to give atom variables normal names.)  The reason for this
convention is that sometimes it's convenient to have both variables
containing an atom and variables containing a dereferenced version of
the same data.  These might be in the same function, or they might be
used in different functions; either way it's important to know which is
which.  A naming convention makes this distinction clear.  (Using a
prefix means that when you add the `@` sign, you get a little jumble of
non-alphabetic characters at the beginnings of variables, which can be a
little bit hard on the eye.  Using a suffix that is not "@"--an obvious
choice--seems easiest to read, because then the visual meaning of `@` is
always the same.)
