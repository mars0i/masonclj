# masonclj

A very small library to ease use of MASON with Clojure.  It provides a
single namespace, `masonclj.simparams`, which defines a single macro,
`defparams`, for use in a Clojure source file that's intended to
subclass MASON's `sim.engine.SimState` (which will be done, along
with other things, by `defparams`).

## Usage

1. Add a dependency to `mars0i/masonclj "0.1.0"` (or whatever is the latest
version number in project.clj or pom.xml here).  For example, if you are
using Leingingen, add `[mars0i/masonclj "0.1.0"]` to the
`:dependencies` vector in project.clj.

2. See [doc/defparams.md](doc/defparams.md).

## License

This software is copyright 2016, 2017, 2018, 2019 by [Marshall
Abrams](http://members.logical.net/~marshall/), and is distributed under
the [Gnu Lesser General Public License version
3.0](https://www.gnu.org/licenses/lgpl.html) as specified in the file
LICENSE, except where noted, or where code has been included that was
released under a different license.
