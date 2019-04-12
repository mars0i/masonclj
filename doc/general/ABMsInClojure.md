Notes on writing agent-based models in Clojure
===

Marshall Abrams

These remarks *may* be incomplete, and *definitely* are the result of my
sometimes biased intuitions and unsystematic, un-thorough
research---which may be outdated anyway.

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

(If you want to get a feel for common patterns in agent-based
modeling, experiment with some of the models in
[NetLogo](https://netlogoweb.org)'s models library. 
NetLogo is a very
popular modeling environment and language for agent-based modeling. 
The language is pretty easy to learn, and it has
many features designed to make agent-based modeling easy.)


### FP and ABMs: general challenges

A central idea of agent-based models is that agents persist over time,
and that their internal states change, or their relationships to each
other and the environment change, or all of the above.  For example, a
model might include organisms with internal energy levels that change,
and that move around in an environment.

There's no reason in principle that this can't be handled in a purely
functional manner. A method that I like, other things being equal, is to
define a
["next-population-state"](https://github.com/mars0i/popco2/blob/master/src/popco/core/main.clj#L51)
function and then throw that and an initial state into `iterate`.  Then
you can `take` as many time steps as you want, or `map` functions
through the sequence to create side-effects such as writing data to a
file.  You can back up and look at earlier stages at any point.

However, it *is* very natural to model agents as persistent data
structures with internal states that are imperatively modified.  There
are various ways to do just this in Clojure, but you lose a lot of the
conveniences of that Clojure provides.  For example, you can define
agents as `deftypes` that are set up to be imperatively modified, or you
can put atoms in the fields of a `defrecord`, but `deftypes` are less
convienient that `defrecords`, and constantly `swap`ing on atoms
clutters your code.  (See item 2 in the discussion of MASON below for an
additional challenge that can arise with defrecords.)

In many ABMs, agents move on a grid.  If your model includes this
sort of behavior, you will probably want to use one or more matrices or
two-dimensional array structures to represent the field on which
agents move.  If all agents are, theoretically, moving simultaneously,
then you can update the matrices functionally--in the sense that a
matrix goes into a function, and a new, modified matrix comes out
(although within the function you probably need to use imperative
methods to fill the new matrix).

However, in many models agents move sequentially or move at different
times for other reasons.  You don't have to allow such "asynchronous"
movement, but it might be a more accurate way to model some systems, and
it could be slightly easier to code.  (With simultaneous movement, you
may need to manage agents that want to move to the same spot.)  If you
try to do this purely functionally, you would probably have to create a
new matrix for every movement by a single agent, even though most of the
matrix is unchanged. So for this kind of model, imperative updating of a
single matrix be significantly more efficient.  (Fortunately, there are
good matrix libraries for Clojure, or you can use Java data structures.)

### Agent-based modeling libraries for Clojure?

You can write an ABM in any language, of course, but it's nicer if you
have a library or environment that's designed for ABMs.  This is why,
although I have written an ABM in pure Clojure, I probably won't do
that  often.

AFAIK there are no ABM libraries written in Clojure.  I don't expect
to see any soon, since the intersection of those interested in Clojure
and in ABMs seems small.

However, there are a few Java ABM libraries, and at least one Javascript
(Coffeescript, actually) ABM library.  So one can consider using them
with Clojure.  I have not spent time researching every library that
might possibly be useful.  Rightly or wrongly, I don't bother 
examining libraries that do not seem to be widely used and regularly
maintained. (Someone else may want to cast a wider net, and might find 
something very worthwhile to use, but I haven't have time.  I'm open to
suggestions, though.)

Some available ABM libraries and environments are listed here:
[comparison_of_agent-based_modeling_software](https://en.m.wikipedia.org/wiki/Comparison_of_agent-based_modeling_software).
Some of the tools seem to be designed for special, narrow purposes, and
I suspect that some (e.g. Swarm?) may be old and not well maintained.

### A browser-based ABM library: Agentscript

This is an ABM library written in Coffeescript.  I have
[experimented](https://github.com/mars0i/clj-agentscript1) a little bit
with using it with Clojurescript.  That seemed pretty easy once I figued
out how Agentscript worked.  (The docs were not ideal.) Agentscript
seems like a nice library, but I decided I wanted more than it offerred,
and I decided to use a Java library.


### Java ABM libraries:

Using my very crude search heuristic (see above), there appear to be exactly
two Java ABM libraries worth considering for use with Clojure:  Repast,
and MASON. Both seem to be used quite a bit in the ABM community.

#### Repast

I investigated [Repast](https://repast.github.io/index.html) a little bit, and the design and documentation
didn't appeal to me.  It seemed more difficult to
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
ways in which Clojure is "[opinionated](https://clojure.org/reference/datatypes#_datatypes_and_protocols_are_opinionated)" mean that it is not intended to make it easy to deal with certain
kinds of common Java designs.

Of the four MASON/Clojure issues listed below, I've
written helper functions and macros to address the first three.  (The
third is unlikely to be a problem for most models in any event.) The
fourth could make a model slow, but only with certain kinds of models.

**Issue 1:** If you add some Bean-style accessors for model parameters,
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


**Issue 2:** The natural thing to do is to define your agents using
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
out of the box with `defrecord` agents because when you
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

**Issue 3:** If you add type hints to avoid reflection for the sake of
speed, and you're not careful, you can get cyclic dependencies that
won't compile.  The design of MASON can make it easy to end up in this
situation.  (Java doesn't prevent cyclic dependencies in the way that
Clojure does, so there would be no reason to expect MASON's design to
reflect concerns about cyclic dependencies.) One feature in 
[`defparams`](https://github.com/mars0i/masonclj/blob/master/doc/masonclj/params.md)
attempts to make it easier to avoid cyclic dependencies with type
hints. However, if your model is fast enough without type hints, or
type hints would be unlikely to speed it up, then there's no reason to
worry about this anyway.

**Issue 4:** Another issue with defrecords occurs *if* you want your
agents to move with continuous coordinates rather than on a grid. 
MASON implements continuous coordinates with a hashtable, using the
`hashCode()` method, defined originally by the Java `Object` class.
However, `defrecord` overrides the normal pointer-identity `hashCode`
method, defining a defrecord's `hashCode` and `equals` methods over
all of the contents of the defrecord instance's fields.  That is,
defrecord objects have the same hash code if and only if they have the
same fields with the same contents; it doesn't matter whether they are
physically distinct. Hashing on all of the field contents is slow
compared to hashing on a pointer, so a MASON/Clojure model with
continous coordinates using  defrecords might be *a lot* slower than
the same model written in Java.  You can speed  things up by using
deftypes that are defined to be updated imperatively, but your code
will be less idiomatic, and you'll have to work harder to write it (it
won't be as fun).  However, many ABMs use movement on a grid--or don't
use movement at all--and don't need continous coordinates, in which
case defrecords' `hashCode` function shouldn't slow things down. (It's
[possible](https://github.com/mars0i/masonclj/blob/master/example/src/example/snipe.clj#L67)
to [override some of the Object
methods](https://clojuredocs.org/clojure.core/defrecord) defined for
defrecords by Clojure, *but not* the `equals` and `hashCode` methods. 
This is undocumented afaik. Try it.) 

#### Netlogo? No.

Could you use NetLogo from Clojure?  NetLogo is written in Java and
Scala, so in theory you could call it from Clojure.  There are a few
well-defined ways to interact with NetLogo from external code, too.  You
could write custom extensions in Clojure, for example.  However, what
one really wants is a general interface between Clojure and the NetLogo
language, since that DSL is the interface to all of NetLogo's ABM
capabilities.  Implementing a Clojure-to-NetLogoDSL interface would be a
lot of work.  It's easier to use Clojure with an ABM library that's
designed to allow you to write models in Java itself.  Or to use
NetLogo's own language.


### Other resources

There are [some
books](https://www.amazon.com/s?k=agent-based+modeling&ref=nb_sb_noss)
on agent-based modeling.

Journals that routinely include agent-based modeling papers include
*Journal of Artifical Societies and Social Simulation*, *Artifical
Life*, *Journal of Theoretical Biology*, *Ecological Modeling*, and
*Complex Adaptive Systems Modeling*.  Those are just a few that occur
to me given my interests and experience; no doubt there are many
others. Also, many other scientific journals, as well as philosophy of
science journals, include ABM-based papers regularly, though not
necessarily in most issues.
