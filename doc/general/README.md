General notes on Clojure, MASON, and ABMs
====

These documents don't concern the masonclj library per se, though
they do provide rationales for some of the design choices assumed by
masonclj.  These notes might be useful for anyone interested in using
MASON with Clojure, or interested in writing ABMs in Clojure.  Some of
the notes are about Clojure-Java interop, and might be useful to a wider
audience.

NOTE: "Agent" in these documents refers to agents in the agent-based
modeling sense.  This sense of the term has almost nothing to do
with the Clojure language agent concept.

## Files in this directory:

* [ABMsInClojure.md](https://github.com/mars0i/masonclj/blob/master/doc/general/ABMsInClojure.md):
General remarks on writing ABMs in Clojure.

* [functionalMASON.md](https://github.com/mars0i/masonclj/blob/master/doc/general/functionalMASON.md):
 Notes on strategies for writing in a more functional-programming style using MASON.

* [ClojureMASONinteropTips.md](https://github.com/mars0i/masonclj/blob/master/doc/general/ClojureMASONinteropTips.md):
Notes on Clojure-Java interop options relevant to use of Clojure with MASON. 


## Projects that might be of interest:

[spork: Spoon's Operations Research Kit](https://github.com/joinr/spork)

[simpro-science: Simulation of spatial processes in Protege-frames by scenarios](https://github.com/rururu/simpro-scene)

Rich Hickey wrote an agent-based simulation inspired by ants.  I don't
think the original web location exists, but if you do a search for ants.clj, you'll
find many variants.

[masonclj: Library to ease use of the MASON ABM library with
Clojure](https://github.com/mars0i/masonclj) (This repo.)
