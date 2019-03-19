Notes toward functional-style MASON
====

## defrecord vs. deftype

(cf. ClojureMASONinteropTips.md)

Note it's possible to use defrecord with protocols if needed.  This
allows a defrecord to implement a MASON interface.  cf.  SIM.clj in
intermittran, in which the deftype Indiv implements MASON's Oriented2D
interface.  You can do something similar with defrecord.  
I'd rather keep the agents free of explicit MASON stuff, but it doesn't
actually hurt anything (other than code modularity and library inclusion)
to do this.

The real display stuff is all Protrayal stuff.  In intermittran,
I was able to put all of that in SimUI.clj (but made a decision
to put that one Oriented2D interface into Sim.clj).


## Is it necessary to run the scheduler?

Yes, it appears so.  Even if the model itself isn't controlled by the
scheduler--if say, you're just `take` from a lazy sequence of model
states--it's the scheduler that makes the UI window repaint.  cf. page
24 of the v.19 manual.  (You make this happen by calling
Display2D.reset().)  


## But could I step the scheduler myself?

i.e. rather than the MASON scheduler driving Clojure code, let Clojure
drive the MASON UI.

Well there's a step() function in Schedule.  Note though it will only do
anything if there are things registered on the schedule.  So I would
have to add things to the schedule before calling step(), or maybe
just leave them on there but repeat.

But how do you pause it?  This is clearly possible, since you can do
this in the GUI.  But I think that step() is exposed mainly so people
can override it in a subclass.  

(Note though that step() in Steppable is something completely
different.)

In the MASON source, look at the second def of `pressPause() in
display/Console.java.  This calls setPlayState().  The comments there
suggest that we can call pressPause, but that we shouldn't mess with
setPlayState() or anything further.

Question: If I *can* do this repeated pausing and starting, is it a
good idea?  e.g. will it make the thing a lot slower?  Note that in
Console.java, pausing involves messing with the state of a thread.


## Is it necessary to extend SimState?

Summary: yes

Why?  

1. To get at a spun-up MersenneTwister RNG.
2. To support user parameter configurations from the GUI.
3. If you use MASON's scheduling facility, that comes from SimState, too.
4. You do need a GUIState if you want to have a UI, and the only
   constructor for GUIState listed in the docs requires that you pass 
   in a SimState.

### discussion:

It's clear you need to extend MASON's GUIState to display stuff via
MASON.  Is it necessary to have a class that extends its SimState?
What does SimState provide?

Note that GUIState does *not* extend SimState.  You must pass a SimState
to GUIState's constructor.  So purely formally, you have to create a
SimState in order to use GUIState.

But what does the SimState give you?  Can you just let it sit there,
unused?

One of the main things SimState provides, if you use it, is the ability
to modify parameters in the simulation from the gui.  So, OK, this is
important enough to use it.  I can set those things from a repl, or from
a commandline, but having them modifiable from the GUI is definitely
useful.

Note that this means that you have to use gen-class's state field, and
use atoms or deftype or something else to allow modification of
parameters.  So you have to have a whole bunch of boilerplate for each
parameter you want accessible through the GUI.

Or just write SimState in Java. :-(

(SimState provides step() and start(), but you can use GUIState's
intead, which will call the SimState's scheduler, for example.)


## Do you have to store simulation parameters in your SimState?

The actual data can go wherever you want.  It's at best only marginally
going to be data in the class that extends SimState, since `gen-class`
gives you only one modifiable field, so if you want more than one
element, you're going to stick a map or defrecord or something into that
data element.  And I don't think you really have to store it there,
actually.  The GUI is going to access the data using bean accessors
anyway, so you could put the data anywhere they can find it.  Heck could
be closures, or another namespace.  It could even be functionally
generated data that's never modified per se, as long as you maintain
some way that the bean accessors can find the current value.  (OK, that
might require atoms.  But still--it doesn't have to be anywhere
particular.)


## Can the program's running state be purely functional?

In my current conception, the internal animal processes, their births
and deaths, etc. can be functional.  I think it's going to make most
sense to use MASON's grid structure(s) for the underlying world.
They're already designed for this purpose, will return Moore
neighborhoods easily, have a hex version, etc.

Q: Can I update a single grid structure (or two or four), as one's
supposed to do in MASON, and pass it along with the functionally updated
animals?  Or is it better to create a new grid for each step, as one
should do in Clojure--especially if the model is lazy?

A: One can get away with using the same data structure, I think.
This will work when you use MASON to pull the system along, and
it's OK up to a point at a repl.

But you loose the ability to go back in time by looking at different
stages of the sequence if you reuse the same grid.  Because the old grid
is the same as the most recent grid, so it won't match the state of the
animals at that earlier time.

On the other hand, maybe it will take up a lot of RAM to store
one grid per tick.  Also maybe it's silly to store old grids
if you're just walking the program using the MASON scheduler
from the GUI.

Q: If I did update the grids functionally, i.e. just created a new one
for each step, does this cause problems with their display?  What do I
have to do to get the Field Portrayal that displays the content of the
grid to connect to a new grid each time?

A: It looks like I just have to call `.setField` on the Portrayal
to assign it the new grid each time.

### outcome:

*In the end* I decided to update all fields functionally--i.e. each
major step in `next-popenv` returns a new snipe field and/or mushroom
field.  snipes also are updated functionally using `assoc` or
`update`.

Then in the schedule loop in `SimConfig/start`, I `swap!` in the new
popenv on every tick, and (this is the crucial step) in
`UI/setup-portrayals`, I use `scheduleRepeatingImmediatelyAfter` to add
a task to the UI part of the scheduling system that calls `setField` on
the snipe field portrayal and mushroom field portrayal on every tick.


## Is functional style slower?

So far in pasta, the speed difference between non-functional and
functional versions are so small that I'm not sure there's even a
difference.  If there is a difference, it's less than 1%.  I observed
this at an early stage, before implementing birth, death, and eating,
when I switched over to functional style from my initial in-place
modification version.  I also observed this at a later stage when I
created a non-functional version to test a hypothesis about why
inspected snipes weren't updating the inspector.  (December 2016: Note
however that I haven't yet started adding type hints all over the place
to avoid reflection.  If avoiding reflection created a big speed
increase, maybe it would reveal a bigger difference between functional
and non-functional styles.  How big could the difference be, though,
given that it's so small now?)

(YMMV.  e.g. in my experiments with Clojure implementations of the
Student ABM from the MASON manual in the `majure` repo, there were big
speed differences between defrecord and deftype because a Continuous2D
field is a hashtable, and hashing is handled differently for these data
structures.  In pasta I use an ObjectGrid2D field, which is just an
array for Objects, so access speed will be the same no matter what you
stick in there.)


## Difficulties with purely functional style

### crossover complications

In a functional-style ABM updating indvididual agents on their own is
easy.  Dealing with with interactions between agents is trickier, and
can be more difficult than what you'd normally write in non-functional
style ABMs.

In `pasta.popenv`, some of the operations are awkward and a bit
complicated because of the need to simultaneously update the snipe
field, the mushroom field, and snipe's internal states.  This would be
simpler with in-place modifications of data structures.  For
illustrations, look at `move-snipes` or `snipes-eat` and the functions
they call.  (The complications are greater than what I dealt with in
popco2 [which didn't use MASON], where I simply had to reorganize
messages from agents in order to deliver them to other agents.)

### inspectors

The default MASON inspectors for agents work, up to a point, without any
special treatement on my part.  Amazingly, I didn't even have to define
bean methods!  Between Clojure and MASON, somehow MASON knew that
defrecord fields hold properties.

In particular, agent inspectors work for one-time, static inspection of
snipes, and it's easy to write a toString that will automatically
display id and energy.

However, the properties won't update in the inspector tab.  This is
apparently because of functional updating.  What the inspector system is
watching is a single object, it seems.  When a snipe moves or changes
energy, that's a brand new object, and MASON can't be expected to know
about it.  I created a quick-and-dirty non-functional-style version in
branch `non-fnl` and saw that snipe properties update properly there.

This is potentially a big drawback of functional style for agent-based
modeling--worse than crossover problems, in a sense.  Even though
crossover issues are fundamental to an ABM, and it's not really
*fundamental* to an ABM that individual agents be watched, in practice
watching individual states can be very useful.  You might "blame" (but
not fault) MASON for the additional complexity because it's not
designed for functional programming, but if you think about it,
providing inspector functionality for a functional-style ABM is by its
nature not really trivial:  You're trying  to watch a "thing" over
time, that's thing isn't actually a single thing in functional style. 
So inspection requires special handling that isn't needed if you just
maintain a pointer to single concrete data structure, as you can
easily do in a non-functional style system.

On the other hand, Clojure makes functional updating very easy, and
imperative updating verbose: You have to use `deftype` with special
annoations *and then* add accessor functions both in a protocol and in
the `deftype` definition, or stick atoms inside the fields in a
`defrecord`, and then either write accessor functions or use `@`,
`swap!`, etc. constantly--but you'd better write the accessor functions
and stick them in protocols too, because otherwise MASON won't know what
to do with them anyway.  This kind of only verbose, only partially
idiomatic, Java-esque programming is what I want to avoid.

### inspector workarounds

I think that I can work around this by passing special arguments to or
writing a subclass of `SimpleInspector` or extending `Inspector` some
other way, maybe using atoms or maps or other lookups by `id` to keep
track of what snipe is being watched.

e.g. a map from ids to snipes.  For any inspected snipe--for any snipe
in that map--on each tick, update it with the new snipe.  This is just
like what I'm doing with the popenv.  But when?  How to find the new
snipe among the bunch?  Can this task be performed at the moment when
the snipe is being replaced?

Could I do something like this?  Add a field to the snipes.  Each
inspector has an atom containing snipe, *and the snipe also has a
pointer to this atom in its special field.  Incestuous.  Can it be
done?  Then at the end of next-popenv, for any such snipe, the atom
will be pulled out and the new snipe swapped in to it.  Yes, I think,
although it's tricky at the repl:

```
user=> (defrecord R [x])
user.R
user=> (def r (R. (atom nil)))
#'user/r
user=> (reset! (:x r) r)
StackOverflowError   java.util.Formatter.parse (Formatter.java:2547)
user=> (reset! (:x r) 14)
14
user=> r
#user.R{:x #object[clojure.lang.Atom 0x54439d52 {:status :ready, :val 14}]}
```

The stack overflow appears to be due to the REPL trying to print out
r, which contained r, which contained ...

This might be very bad if an inspector tries to display that field.  So
it must be hidden.  This is sounding nasty.  Maybe stick with the map.
Maybe just have a boolean flag field that says "I'm inspected-- go look
for me in the inspected map."  e.g.:

```
(when (:inspected? new-snipe)
  (swap! inspected-snipes-map assoc (:id new-snipe) new-snipe))
```

In the end, I used the MASON `Propertied` interface so that each
tick's (usually) new snipe delegates to a Properties class instance in
which there are methods that the inspector will use.  See
`make-properties` and calls to it in snipes.clj. That is,  each snipe
has a `properties` field that's specified by `Propertied`, and this
field contains an instance of a subclass of `Properties`.  The
inspector will be tracking that snipe instance that it found when you
first told it to follow the snipe.  This instance is no longer doing
anything, but the methods in its `Properties` subclass will go and
find the current instance of the "same" snipe in order to get the data
that the inspector should see. This is all pretty time-consuming--the
simulation visible slows when you watch a few snipes--but it only
slows things down when you watch snipes, and you didn't really need
speed then, anyway.  (It's only when you're running without the GUI
that you really need top speed, I feel.  The GUI is already a bit too
fast unless you slow it down.)
