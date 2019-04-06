`masonclj.properties`: `defagt`, `make-properties`
===
Marshall Abrams

`masonclj.properties` provides two Clojure operators, `defagt` and
`make-properties`; both are designed to ease use of 
the [MASON](https://cs.gmu.edu/~eclab/projects/mason) ABM library with 
Clojure.  

`make-properties` makes it easier to use MASON inspectors to track
agents in the GUI even if you implement agents as defrecords--which
don't have modifiable fields, so the same agent might be represented by
different "time slice" defrecords at different times.  And `defagt`
(pronounced "deff agent") makes it simpler to use `make-properties`.

NOTE: "Agent" below refers to agents in the agent-based modeling sense.
This sense of the term pretty much has nothing to do with the Clojure
language agent concept.

## Rationale

(See also
[doc/ClojureMasonInteropTips.md](https://github.com/mars0i/masonclj/blob/master/doc/ClojureMASONinteropTips.md) and
[doc/functionalMASON.md](https://github.com/mars0i/masonclj/blob/master/doc/functionalMASON.md).)

### Inspectors

MASON provides a convenient "inspector" facility to (a) track locations
of agents on the screen, (b) watch values of fields within the agent,
(c) plot values of fields over time, and (d) edit values of fields from
the GUI (but I don't use that).  Usually if you double-click on an
agent, the main window will display information about it, and it will be
circled in the graphical representation of your simulation.  (For detailed
information see chapter 10 of the v.19 [MASON
manual](https://cs.gmu.edu/~eclab/projects/mason/manual.pdf).

### Defrecords with functional updating

One way to create a Java class in Clojure is with `defrecord`, which
creates what is sometimes known as a defrecord class.  Defrecords are
very common in Clojure, and there are numerous associated functions and
techniques that make defrecords a pleasure to use.  So it's natural to
want to use defrecords to represent agents in your ABM.  There are
alternatives, such as `deftype`, but they are less convenient, and take
some of the fun out of Clojure.

Defrecords are functional (in the functional programming sense): When
you update a defrecord you generally create a new object (a new
pointer).  This means that the same agent will often be represented by
multiple JVM objects--multiple "time slices" of an agent--over the
course of a simulation.  This causes no problem for the most part, in my
experience.  It means that when you change the field values in an agent,
you have to replace the old agent-time-slice with the new one in the
data structure representing the agent's location, even if this is only a
location on the screen in the GUI.  MASON will display whatever is in
the data structure on a given time step, and if you are representing
motion, you will see it.  (The data structures holding the agents will
probably have include one or more of MASON's built in collection
classes, such as `ObjectGrid2D`, if you want to display your ABM in
MASON's GUI.  You can update these as imperatively or functionally as
you like, apart from the fact that they must be updated imperatively at
some points.  i.e. if you want to create a new collection for each
timestep, you can.)

### The problem

However, the inspector facility won't work with functionally updated
agents: When you double-click on an agent in the GUI, MASON tracks the
agent by the pointer to the current agent-time-slice available at that
step.  When you update the agent and replace it with a new time-slice,
the inspector functions won't know this, and you will no longer be
tracking your agent.

### The solution

The solution is for the your defrecord to implement MASON's `Propertied`
interface, requires that you provide a `properties()` method that
returns a subclass of the class `Properties`.  Your `Properties`
subclass should implement various methods that can be called by the
inspector facility to communicate with your agent, and determines what
fields the inspector can see.  The most important method is
`getObject()`, which gives you control of what agent the inspector is
getting data from.  What I do is have `getObject` call a method that
will look up the current agent-time-slice of an agent using an id that
can be gotten from the agent's first time-slice--which the one argument
passed to the `properties()` method.

### `make-properties` and `defagt`

Setting up the `Properties` instance returned by `properties()` method
is a little bit of trouble, so I wrote a function to make it easier: ```
make-properties ``` You can use this directly to help create the return
value of the `properties()` method for the `Propertied` interface thaty
your defrecord should implement.  This means that your `defrecord`
should probably have a field named `id` that you will keep filled with a
unique agent id, and a special field named `circled$` that should be
initialized with an atom containing a boolean.  (I always name variables
containing atoms with a final "$".)

Alternatively, you can use the `defagt` macro to define your agent
defrecord.  This automatically adds the `circled$` field, and defines 
a constructor function whose name is "-->" followed by the defrecord
name.  This constructor is just like the normal "->Name" constructor for
defrecords, except that it automatically initializes `circled$` with
`(atom false)`.  More importantly, `defagt` calls `make-properties`
for you, with additional arguments that are passed to it, simplifying
the process of implementing `Propertied`.

## The time-slice look up function

Note that to use either `defagt` or `make-properties`, you will have
to generate a function that always returns the current time-slice of an
agent.  This is the first argument to `make-properties`.  A good way to
do this is to use the argument to the `properties` method of
`Propertied` to get an id or some other information that can be closed
over to produce the needed function.  The third argument to `defagt`
is designed for this purpose.

(In my code, I create a higher-order function that takes a first
argument containing something containing a map from id's to current
agent-time-slices, and a second argument that will be the first
time-slice of a given agent.  You can then use this with `partial` to
create a closure over the data structure, and then pass this closure to
`defagt` or `make-properties`.)


## Use of `make-properties`

```clojure
masonclj.properties/make-properties
([curr-agent-slice & fields])
```

`make-properties` returns a `sim.util.Properties` subclass that can be
returned by the `properties` method implemented by agent defrecord for
its `sim.util.Propertied` interface.  This can allow fields to be
displayed in the GUI on request:  It will be used by the MASON GUI to
allow inspectors to follow a functionally updated agent, i.e. one whose
JVM identity may change over time.  (If an agent type is a defrecord but
is never modified, or agents are objects that that retain pointer
identity when modified, there's no need to implement the `Propertied`
interface.)

The `curr-agent-slice` argument should be a parameterless function that
always returns the current time-slice of an agent.  (The function might 
be a closure over information in the original agent slice that will be
passed to `Propertied`'s `propertie`s method, and this information might be
used to look up the current slice.  See the `defagt` source for 
illustration.) 

The `fields` argument consists of zero or more 3-element sequences in
each of which the first element is a key for a field in the agent, the
second is a Java type for that field, and the third is a string
describing the field.

If the defrecord that implements `Propertied` contains contains a field
named `circled$`, which should contain an atom around a boolean, this
will be used to track whether the agent is circled in the GUI.



## Use of `defagt`

```clojure
masonclj.properties/defagt
([agent-type fields make-curr-agent-slice gui-fields-specs 
  & addl-defrecord-args])
Macro
```
`defagt` defines a defrecord type and a corresponding factory
function:

1. `defagt` will define the defrecord type with the name given by the
`agent-type` argument, and with field names specified in the `fields`
argument (a sequence), plus an additional initial field named
`circled$`.  This can be used to track whether an agent is circled in
the GUI.

2. `defagt` defines a special factory function, named with the
defrecord type name prefixed by "-->", that accepts  arguments for the
fields specified in `defagt`'s `fields` argument, passing them to the
usual "->" factory function.  The "-->" factory function but will also
initialize the `circled$` field to `(atom false)`, so by default
the agent will not be circled in the gui.

3. In addition to any user-supplied `defrecord` options and specs
specified in the `addl-defrecord-args` argument, the new defrecord will
override `java.lang.Object`'s `toString` method to incorporate an agent
id if `id` is included in the `fields` argument, and will implement the
MASON `sim.util.Propertied` interface.  `Propertied` has one method,
`properties`, which is passed the first time-slice of an agent and
returns an instance of `sim.util.Properties`.  The generated code does
this by calling `masonclj.properties/make-properties`. `defagt` passes
to `make-properties` the result of applying its `make-curr-agent-slice`
argument to the first time-slice.  The `make-curr-agent-slice` function
should return a "current agent slice" function that can look up and
return an agent's current time-slice using information in its first
time-slice.  `defagt` also passes its `gui-field-specs` argument to
`make-properties`.  See documentation on `make-properties` for more
information on its parameters.  (Note that `make-properties` prefers
that the defrecord have a `circled$` field, which is why `defagt` adds
`circled$` to the new defrecord type.)
