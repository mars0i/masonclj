Notes on writing agent-based models in Clojure
===

Marshall Abrams

These remarks may be incomplete, and are the result of my sometimes
biased intuitions and unsystematic, un-thorough research---which may be
outdated.

### What are ABMs?

"[Agent-based model](https://en.m.wikipedia.org/wiki/Agent-based_model)" (ABM) and "individual-based model" are used to that
refer to simulations that usually involve a large number of interacting
"agents", i.e. software entities whose behaviors are determined by
(usually) relatively simple bits of code.  What's interesting about ABMs
is seeing what happens when a lot of agents interact over time.  That's
a vague description of the paradigm, but the boundaries of the category
are necessarily vague.  I've seen ABMs in which the agents represent
people, or entities within minds, or companies, or associations of
villages in Bali, or proteins within E. coli, etc.  Those are ones that
come to mind.

Often ABMs have a graphical component, so that you can watch the agents
interacting over time.  This isn't essential, but being able to watch
the simulation play out helps you understand what happens, fine tune the
model, and investigate particular model runs in more detail.  (Often
there are random elements in ABM simulations, and any good ABM system
will use a pseudorandom number generator that allows you record PRNG
seeds and use them to replay particular runs.)  On the other hand, once
you know what you are doing with a model, you might want to perform a
series of runs and record data from them.  This will be faster if you
can turn off the graphical component.

(If you're not familiar with agent-based modeling, you might want to experiment
with NetLogo before working in ABMs in Clojure.   NetLogo is a very popular
modeling environment and language for agent-based modeling.  The language
is pretty easy to learn, and the language and environment have many features
designed to make agent-based modeling easy. So if you want to get a feel for
common patterns in agent-based modeling, download NetLogo or run the web
version, and experiment with various sample models available in the
Models Library dropdown.  There are docs and source code for each on the
Info and Code tabs, respectively.)


### FP and ABMs: general challenges

A central idea of agent-based models is that agents persist over time,
and that their internal states change, or their relationships to each
other and the environment change, or all of the above.  For example, a
model might include organisms with internal energy levels that change,
and that move around in an environment.

It's very natural to model agents as persistent data structures with
internal states that are imperatively modified, and to treat the
environment in which they move as a persistent data structure in which
agents' locations are imperatively modified.  There are various ways to
do just this in Clojure, but you lose a lot of the conveniences of that
Clojure provides.  For example, you can define agents as `deftypes` that
are set up to be imperatively modified, or you can put atoms in the
fields of a `defrecord`, but `deftypes` are less convienient than
`defrecords`, and constantly `swap`ing on atoms clutters your code.

TALK ABOUT IDENTITY OVER TIME HERE

For an environment, you can use a core.matrix or Java matrices or arrays
that you modify imperatively.  This is OK, but you have to be careful.
It is sometimes reasonable to update environments functionally; this
works if you can structure your code so that all changes of
relationships to the environment or of relationships between agents
happen simultaneously.  For some models, this will be unreasonable,
though.  In practice, even if you structure you code to update large-scale
things like positions in environments in a functional manner, you will
probably want to do selected updates imperatively.

### Agent-based modeling in Clojure?

You can write an ABM in any language, of course, but it's nicer if you
have a library or environment that's designed for ABMs.  This is why,
although I have written an ABM in pure Clojure, I probably won't do that
again.

A subset of the ABM libraries and environments are listed here:
[comparison_of_agent-based_modeling_software](https://en.m.wikipedia.org/wiki/Comparison_of_agent-based_modeling_software).  Some of the tools seem to be designed for special, narrow purposes, and I
suspect that some (e.g. Swarm?) may be old and not well maintained.

AFAIK there are no ABM libraries written in Clojure.  I don't expect
to see any soon, since the intersection of those interested in Clojure
and in ABMs seems small.

However, there are a few Java ABM libraries, and at least one Javascript
(Coffeescript, actually) ABM library.  So one can consider using them
with Clojure.  I don't spend a lot of time researching every library that
might possibly be useful.  When choosing ABM libraries, I haven't tried to
investigate libraries that do not seem to be widely used and currently
maintained. Someone else might want to cast a wider net, and might find 
something very worthwhile to use, but I don't have time.  (I'm open to
suggestions, though.)

### A browser-based ABM library: Agentscript

This is an ABM library written in Coffeescript.  I have [experimented](https://github.com/mars0i/clj-agentscript1) a
little bit with using it with Clojurescript.  That seemed pretty easy
once I figued out how Agentscript worked.  (The docs were not ideal.)
Agentscript seems like a nice library, but I decided I wanted more
than it offerred, and I decided to use a Java library.


### Java ABM libraries:

Using my crude search heuristic (see above), there appear to be exactly
two Java ABM libraries worth considering for use with Clojure:  Repast,
and MASON. Both seem to be used quite a bit in the ABM community.

(Could you use NetLogo from Clojure?  NetLogo is written in Java and
Scala, so in theory you could call it from Clojure.  There are a few
well-defined ways to interact with NetLogo from external code, too.  You
could write custom extensions in Clojure, for example.  However, what
one really wants is a general interface between Clojure and the NetLogo
language, since that DSL is the interface to all of NetLogo's ABM
capabilities.  Implementing a Clojure-to-NetLogoDSL interface would be a
lot of work.  It's easier to use Clojure with an ABM library that's
designed to allow you to write models in Java itself.)

#### Repast

I investigated [Repast](https://repast.github.io/index.html) a little bit, and the design and documentation
didn't appeal to me.  It seemed more confusing and more difficult to
use with Clojure than MASON.  I might be wrong about that, and I know
that Repast has fans, but I decided to use MASON. If you're interested
in Repast, please feel free to try it, and let me know what you think
if you feel like it.

#### MASON

[MASON](https://cs.gmu.edu/~eclab/projects/mason/) is a well thought out, well-designed, very flexible ABM library.
It has many advanced capabilities.

Clojure is a well thought out, well-designed, very flexible language.
(It is a lot of fun, too, which is intimately tied how easy it is to
get a lot done with Clojure.)

However, MASON's design uses a lot of inheritance, and has some other
features that make it a bit awkward to use with Clojure.  Some of the
difficulties are due to the the FP-orientation of Clojure, and the fact
that there's no reason that a Java ABM library should worry about FP
concerns.  Some of the difficulties come from the fact that some of the
ways in which Clojure is "[opinionated](https://clojure.org/reference/datatypes#_datatypes_and_protocols_are_opinionated)" language" mean that it is not intended to make it easy to deal with certain
kinds of common Java designs.

**Item 1:** If you add some Bean-style accessors for model parameters,
MASON will automatically tie them to GUI elements, so that you can
control the model from the GUI.  This is very nice.  However, the
accessors have to be tied to data that would normally be stored in a
subclass of MASON's SimState class.  In addition, it appears that the
only way to get all of the effects of subclassing that are needed is to
use the poorly documented, black art of `genclass`.  (`proxy`, `reify`,
`defrecord`, and `deftype` are not enough.)  What this means is that for
each model parameter that you want controlled from the GUI, you will
probably want:

1. A map, defrecord, etc. that you place in the single instance
variable allowed by `genclass`, containing a data element for each 
parameter.

2. An instances state initialization function that initalizes each of
those data elements.

3. At least two accessor functions for each data element.

4. A declaration for each of these functions in the `:methods` element
of the `genclass` map.

If you also want to be able to set these parameters from the command
line, then you'll also need:

5. Corresponding elements in your configuration for `clojure.tools.cli`
or whatever method you use for processing command line options.

When you add or change anything concerning model parameters, you may
have to update all of the above at the same time.  (I defined a big
macro, [`defparams`](https://github.com/mars0i/masonclj/blob/master/doc/masonclj/params.md),
to write all of this code and keep it coordinated.)


**Item 2:** The natural thing to do is to define your agents using
`defrecord`.  This can work quite well.  It means that when you
you are doing functional and not imperative updating to agents'
internal states, but you can just update the arrays that 
MASON expects agents to live in, and it will dutifully display the
agents in the GUI, ignoring the fact that you removed an old agent
and inserted a brand new one that represents the same entity, as far as
you are concerned.

However, this will break down if you want to use MASON's built-in method
for displaying an agent's internal state while it runs.  You can
double-click on an agent in the GUI, and it will be highlighted and you
will see its internal variables in another window.  This is very useful
sometimes.  This doesn't work
outof the box with `defrecord` agents because when you
decide to "watch" an agent, MASON stores a pointer to it, and when you
do a functional update of the defrecord, the pointer now points to
the old version of the agent, which will never change.  You could
instead use a `deftype`, but then you lose the many conveniences of
defrecords, and Clojure becomes less fun, ... and less Clojurely.
(Since one of the main reasons I wanted to use MASON with Clojure was
that I wanted the convenience and fun of working Clojure, this is not
an option I like.)

Fortunately, MASON provides a workaround for this situation; you can
define a method that MASON can call to get the current state of an
agent, but it's a bit of work to write.  I defined a function
[`make-properties`](https://github.com/mars0i/masonclj/blob/master/doc/masonclj/properties.md) and a macro `defagent` to do most of the work for you.

**Item 3:** I you add type hints to avoid reflection for the sake of
speed, and you're not careful, you can get cyclic dependencies that
won't compile.  The design of MASON can make it easy to end up in this
situation.  (Java doesn't prevent cyclic dependencies in the way that
Clojure does, so there would be no reason to expect MASON's design to
reflect concerns about cyclic dependencies.) One feature in `defparams`
attempts to make it easier to avoid cyclic dependencies with type hints.
However, if your model is fast enough without type hints, or type hints
would be unlikely to speed it up, then there's no reason to worry about
this issue.

**Item 4:** Another issue with defrecords occurs *if* you want your agents to
move with continuous coordinates rather than on a grid.  MASON
implements continuous coordinates with a hashtable, hashing on the
identity of the agent.  The hash function that Clojure defines for
defrecords causes the hash to be defined over all of the contents of
the defrecord instance's fields.  This is slow compared to hashing on
a pointer, so a model with continous coordinates using defrecords might
be *a lot* slower than the same model written in Java.  You can speed 
things up by using deftypes that are defined to be updated imperatively,
but your code will be less idiomatic, and you'll have to work harder to
write it.  (It's possible to [overide some of the methods](https://clojuredocs.org/clojure.core/defrecord) defined for
defrecords by Clojure, *but not* the equality and hash methods.  This
is undocumented afaik.  Try it.)  However, many ABMs use movement on
a grid, and don't need continous coordinates, in which case you can
use defrecords without a slowdown due to hashing.

