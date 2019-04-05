masonclj
===

A very small library that provides functions and macros that smooth a
few rough edges to using MASON in Clojure.  (Most of the rough edges are
due to reasonable design choices by the architects of both Clojure and
MASON that sometimes don't fit together well.)

## Usage

1. Add a dependency to `mars0i/masonclj "0.1.0"` (or whatever is the latest
version number in project.clj or pom.xml here).  For example, if you are
using Leingingen, add `[mars0i/masonclj "0.1.0"]` to the
`:dependencies` vector in project.clj.

2. The
[README](https://github.com/mars0i/masonclj/blob/master/doc/README.md)
in the **doc** directory is a starting point for detailed information on
what masonclj provides and how to use it.

3. The *example* directory contains a simple MASON model in
Clojure, using masonclj.

## What else do you need to know?

The documentation here does not provide introductions to Clojure or
MASON.  MASON has a detailed PDF manual that begins with a tutorial and
a example, but to read the MASON manual, you'll need to know a little
bit of Java or have enough experience to figure it out as you read.  (My
"majure" repo includes some Clojure implementations of the Students
example from the MASON tutorial.) You will also have to learn a bit more
about Clojure-Java interop than is usually necessary, but studying the
source under the *example* directory with good interop resources at hand
is probably a good strategy.

Not all of the corners of Clojure-Java interop are well documented.  The
book <em>Clojure Programming</em> by Emerick et al.  from O'Reilly is
the best single source of information about Clojure-Java interop that I
have encountered.  Some of the documents here may help, but they
don't go into detail.

## License

This software is copyright 2016, 2017, 2018, 2019 by [Marshall
Abrams](http://members.logical.net/~marshall/), and is distributed under
the [Gnu Lesser General Public License version
3.0](https://www.gnu.org/licenses/lgpl.html) as specified in the file
LICENSE, except where noted, or where code has been included that was
released under a different license.
