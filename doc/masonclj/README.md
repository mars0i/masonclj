masonclj documentation
====
Marshall Abrams

NOTE: "Agent" below refers to agents in the agent-based modeling sense.
This sense of the term pretty much has nothing to do with the Clojure
language agent concept.

## Overview

masonclj provides two kinds functions and macros for two purposes:

* Making it easier to define parameters for control of a model via the
  GUI and the command line.
* Making it easier to track agents using the MASON inspector
  facilities if you use defrecords to implement agents.

(See the doc/general directory for notes that provide rationales for
some of the design choices assumed by masonclj.)

## To get started with masonclj

You'll need Clojure, Leiningen, MASON, and Java.

Add a dependency to `mars0i/masonclj "0.1.0"` in your Leiningen
project.clj, or the equivalent for whatever build management system
you're using.  For Leingingen, add `[mars0i/masonclj "0.1.0"]` to the
`:dependencies` vector in project.clj.  Replace "0.1.0" with whatever
is the latest release version in the project.clj in the masonclj repo.

Eventually, I'll upload masonclj to Clojars, and at that point, running
`lein deps` should automatically install masonclj.  Before that, you
first need to install the masonclj git repo, and then run `lein install`
from the repo's root directory.

When you download MASON from the [MASON
website](https://cs.gmu.edu/~eclab/projects/mason), there are some
supplementary libraries available there that you should also download.
I usually have the following in a project.clj for a MASON project:

```clojure
  :dependencies [[org.clojure/clojure "1.10.0"]  ; Clojure version
                 [org.clojure/tools.cli "0.4.1"] ; a command line processing library
                 [org.clojure/math.numeric-tower "0.0.4"] ; for a few functions
                 [mars0i/masonclj "0.1.0"]
                 [mason "19"]
                 ;; libs used by Mason:
                 [javax.media/jmf "2.1.1e"]
                 [com.lowagie/itext "1.2.3"] ; comes with MASON--not 1.2
                 [org.jfree/jcommon "1.0.21"]
                 [org.jfree/jfreechart "1.0.17"]
                 [org.beanshell/bsh "2.0b4"]]
```

(TODO: How to install these in a maven repository.)


## Model parameters coordination

See [params.md](https://github.com/mars0i/masonclj/blob/master/doc/masonclj/params.md)
for documentation on the `defparams` macro in the `masonclj.params`
package.  `defparams` does two things:

1. Generates a series of coordinated definitions for model parameters.
It's useful to include a series of related definitions for model
parameters.  In Clojure, many of these would most naturally be put in
diffent locations in your code, and in some cases they *must* be put
in different places in your code.  In that case adding or removing a
parameter or changing its definition can lead to having to update
several parts of your code.  This is a pain and can lead to bugs if
your model has a lot of configurable parameters.  `defparams`
generates these definitions all at once, making it easier to keep them
coordinated.

2. Moves global configuration data into its own namespace.  There
are some situations in which it's useful to put global
configuration data in a separate namespace to avoid cyclic
dependencies (which Clojure doesn't always allow).  The global
configuration data is used by the model parameter definitions.

## Tracking agents in the GUI

See
[properties.md](https://github.com/mars0i/masonclj/blob/master/doc/masonclj/properties.md)
for documentation on the definitions in `masonclj.properties`.  These
allow you to use functionally updated defrecord objects as MASON
agents,
while still making it possible to track an agent in the GUI using
MASON's inspector functionality.

## Notes on variable naming

General naming convention: I sometimes use CamelCase to name things
that have a Java-ey role with MASON, but mostly use Clojure-standard
kebab-case.

Variables containing Clojure atoms: I name variables that contain atoms
with "$" at the end of their names. The norm in Clojure is to give atom
variables normal names, but I find that sometimes it's convenient to use
a variable containing an atom containing data and a variable containing
a dereferenced version of the same data.  These might be in the same
function, or they might be used in different functions; either way it's
important to know which is which.  (I got tired of accidentally
confusing them and having to deal with the resulting bugs.)  A naming
convention makes this distinction clear.  

(Why a "$" suffix? Using a non-alphabetic prefix would mean that when
you added the `@` sign, you'd get a little jumble of non-alphabetic
characters at the beginning of a variable, which is visually difficult
to parse.  Using a string of alpha characters is too verbose.  Using a
suffix that is not "@"--an obvious choice--seems easiest to read,
because then the visual meaning of `@` is always obvious.)
