masonclj documentation
====

## masonclj:

### Model parameters coordination:

See [params.md](https://github.com/mars0i/masonclj/blob/master/doc/params.md)
for documentation on the `defparams` macro in the `masonclj.params`
package.  It does two things:

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

### Tracking agents in the GUI:

See
[properties.md](https://github.com/mars0i/masonclj/blob/master/doc/properties.md)
for documentation on the `defagent` macro and the `make-properties`
function in the `masonclj.properties`.  These allow you to use
functionally updated defrecord objects as MASON agents while still
making it possible to track an agent in the MASON GUI using MASON's
inspector functionality.



## Miscellaneous notes:

### Variable naming

General naming convention: I sometimes use CamelCase to name things
that have a Java-ey role with MASON, but mostly use Clojure-standard
kebab-case.

Variables containing Clojure atoms: I name variables that contain atoms with "$" at the end of their
names. This is nonstandard; the norm in Clojure is to give atom
variables normal names. Sometimes it's convenient to have both a
variable containing and atom and one containing a dereferenced version
of the same data.  A naming convention makes it clear which is which.
Using a suffix that is not "@" (an obvious choice) seems easiest to
read.  

## Other files in this directory:

* ABMsInClojure.md: General notes on options for writing ABMs in
Clojure.

* functionalMASON.md: Notes on strategies for writing in a more
functional-programming style using MASON.

* ClojureMASONinteropTips.md: General notes on Clojure-Java interop
relevant to use of Clojure with MASON.  This document came from my
experiments implementing MASON's Students example in Clojure (see the
<a href="https://github.com/mars0i/majure">majure</a> repo).  This
document reflected my focus at the time on producing code that was as
fast as possible.  After the majure experiments, I applied what I'd
learned in the <a
href="https://github.com/mars0i/intermittran">intermittran</a> repo. 
The resulting code in intermittran is not very idiomatic to Clojure,
and unpleasant, imo.  My current approach is to worry more about
trying to write (relatively) idiomatic Clojure rather than trying to
eke out as much speed from MASON as possible, but the interopTips
document provides the background for my approach in pasta, including
the `defsim` macro.

* getName.txt: Some notes about an (unimportant and somewhat obscure)
bug that occurs when using MASON in Clojure.
