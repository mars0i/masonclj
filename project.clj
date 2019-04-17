(defproject mars0i/masonclj "0.2.0"
  :description "masonclj is a small library providing functions and macros
                to make it easier to use the MASON ABM library in Clojure."
  :url "https://github.com/mars0i/masonclj"
  :license {:name "LGPL 3.0"
            :url "https://www.gnu.org/licenses/lgpl.html"}
  :deploy-repositories {"clojars" 
                        {:url "https://repo.clojars.org" :sign-releases false}}
  :dependencies [[org.clojure/clojure "1.10.0"]]
  :repl-options {:init-ns masonclj.core}
  )
