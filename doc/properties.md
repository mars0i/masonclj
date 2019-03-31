`masonclj.properties`: `defagent`, `make-properties`
===

## Rationale

See also doc/ClojureMasonInteropTips.md and doc/functionalMASON.md.

`masonclj.properties` provides potentially useful two functions,
`defagent` and `make-properties`.

### Inspection

MASON provides a convenient "inspector" facility to (a) track locations
of agents on the screen, (b) watch values of fields within the agent,
(c) plot values of fields over time, and (d) edit values of fields from
the GUI (but I don't use that).  Usually if you double-click on an
agent, the main window will display information about it, and it will be
circled in the graphical representation of your simulation.

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

### `make-properties` and `defagent`

Setting up the `Properties` instance returned by `properties()` method
is a little bit of trouble, so I wrote a function to make it easier: ```
make-properties ``` You can use this directly to help create the return
value of the `properties()` method for the `Propertied` interface thaty
your defrecord should implement.  This means that your `defrecord`
should probably have a field named `id` that you will keep filled with a
unique agent id, and a special field named `circled$` that should be
initialized with an atom containing a boolean.  (I always name variables
containing atoms with a final "$".)

Alternatively, you can use the `defagent` macro to define your agent
defrecord.  This automatically adds the `circled$` field, and defines 
a constructor function whose name is "-->" followed by the defrecord
name.  This constructor is just like the normal "->Name" constructor for
defrecords, except that it automatically initializes `circled$` with
`(atom false)`.  More importantly, `defagent` calls `make-properties`
for you, with additional arguments that are passed to it, simplifying
the process of implementing `Propertied`.

## The time-slice look up function

Note that to use either `defagent` or `make-properties`, you will have
to define a function that accepts an agent time-slice as an argument,
and knows how to use it to look up the current time slice of the same
agent.  This is the first argument to `make-properties`, or the third
argument to `defagent`.  In my code, I create a higher-order function
called `make-get-curr-obj` which takes a first argument which contains
(something containing) a map from id's to current agent-time-slices, and
a second argument that will be the first time-slice of a given agent.
You can then use this with `partial` to create a closure over the data
structure, and then pass this closure to `defagent` or
`make-properties`.

## Use of `defagent`

## Use of `make-properties`
