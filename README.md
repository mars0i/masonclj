masonclj
===

Current release: [![Clojars Project](https://img.shields.io/clojars/v/mars0i/masonclj.svg)](https://clojars.org/mars0i/masonclj)


**masonclj** is a very small library that provides functions and macros
that smooth a few rough edges involved in using
[MASON](https://cs.gmu.edu/~eclab/projects/mason) with
[Clojure](https://clojure.org).  The rough edges are due to
reasonable design choices by Clojure and MASON architects; masonclj
makes some available workarounds easier to use.

MASON is a Java library for [agent-based
modeling](https://en.wikipedia.org/wiki/Agent-based_model).  (Note: This
use of "agent" has nothing to do with the Clojure language *agent* concept.)

## What's in masonclj and how does one use it?

See the [masonclj
documentation](https://github.com/mars0i/masonclj/blob/master/doc/masonclj/README.md)
in the doc/masonclj directory.

Also see the *example* directory, which contains a simple MASON model in
Clojure using masonclj.  The masonclj source is under *src*.

## Useful background material

[General-purpose notes on Clojure with MASON or other approaches to
ABMs](https://github.com/mars0i/masonclj/blob/master/doc/general/README.md)
are in the doc/general directory.  This provides rationales for some of
the design choices assumed by masonclj, but might also be of interest
to people who want to write ABMs in Clojure without MASON.  Some of
the Clojure-Java interop notes might of broader interest.

## What else do you need to know?

The documentation here doesn't provide introductions to Clojure or
MASON.  MASON has a detailed PDF manual that begins with a tutorial
and a example. To read the MASON manual, you'll need to know a little
bit of Java or have enough experience to figure it out as you read.
You will also have to learn a bit more about Clojure-Java interop than
is usually necessary, but studying the source under the *example*
directory with good interop resources and the MASON manual and
classdocs at hand is probably a good strategy.  (My *majure* repo
includes some Clojure implementations of the Students example from the
MASON tutorial.  It's possible that it might be useful to look at that
while reading the tutorial in the MASON manual. However, the examples
in that repo are poor illustrations of good Clojure style.)

The book <em>Clojure Programming</em> by Emerick et al.  from O'Reilly
is the best single source of information about Clojure-Java interop I've
read, but I know of no completely comprehensive source.  However, other
books on Clojure have come out more recently, as well as new editions of
older books, so it may be that there are other excellent resources on Java
interop now. I haven't check.  Some of the documents here may help a little,
too, but they don't go into detail.

## And

Please feel free to ask questions, provide feedback, submit issues,
pull requests, etc.

## License

This software and text is copyright 2016, 2017, 2018, 2019 by [Marshall
Abrams](http://members.logical.net/~marshall/), and is distributed under
the [Gnu Lesser General Public License version
3.0](https://www.gnu.org/licenses/lgpl.html) as specified in the file
LICENSE, except where noted, or where code has been included that was
released under a different license.
