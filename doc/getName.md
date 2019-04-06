How to set the MASON title bar name
===
Marshall Abrams

### How to do it

`getName()` is static in `GUIState`.  You can't actually override a
static method, normally, in the sense that the method to run would be
chosen at runtime by the actual class used.  Rather, with a static
method, the declared class of a variable determines at compile time
which method to call.  *But* MASON uses reflection to figure out which
method to call at runtime.  Nevertheless, this means that we need a
declaration in :methods, which you would not do for overriding
non-static methods from a superclass.  Also, the declaration should be
declared static using metadata *on the entire declaration vector.

```
(ns 
  ...
  (:gen-class 
    ...
    :methods [^:static [getName [] java.lang.String]] ; see comment on the implementation below
 ))

...

(defn -getName [] "example app")
```

See https://stackoverflow.com/questions/26425098/how-to-generate-generate-static-methods-with-clojures-gen-class
for the syntax of the `:gen-class` declaration.


### How not to do it

So you don't have to--and should not do the following.

When a method has multiple arities in the super, you you can use a
Clojure multiple-arity method.  But if there are different *types* to
the arguments, then you have to distinguish them by tacking type
specifiers on to the name of the method!

Example:

```
(defn -getName-void [this] "free-agent") 
```

See:

https://groups.google.com/forum/#!topic/clojure/TVRsy4Gnf70

https://puredanger.github.io/tech.puredanger.com/2011/08/12/subclassing-in-clojure (in which Alex Miller of all people learns from random others)

http://stackoverflow.com/questions/32773861/clojure-gen-class-for-overloaded-and-overridden-methods

http://dishevelled.net/Tricky-uses-of-Clojure-gen-class-and-AOT-compilation.html
