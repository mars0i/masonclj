README.md
====
# Code documentation

## What's in files here?

* defparams.md: Documentation on the `defsim` macro in `utils.defsim`.
This is used in Sim.clj and generates code whose effects are used
throughout pasta.

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

## Miscellaneous notes:

* Notes on variable naming: Variables that contain atoms have "$" at
the end of their names. This is nonstandard; the norm in Clojure is to
give atom variables normal names. Sometimes it's convenient to have
both a variable containing and atom and one containing a dereferenced
version of the same data.  A naming convention makes it clear which is
which. Using a suffix that is not "@" (an obvious choice) seems
easiest to read.  I sometimes use CamelCase to name things that have a
Java-ey role with MASON, but mostly use Clojure-standard kebab-case.
