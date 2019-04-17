(defproject example "0.1.0"
  :description "Example of use of masonclj in agent-based simulation using MASON"
  :url "http://example.com/FIXME"
  :license {:name "LGPL 3.0"
            :url "https://www.gnu.org/licenses/lgpl.html"}
  :dependencies [[org.clojure/clojure "1.10.0"]  ; Clojure version
                 [org.clojure/tools.cli "0.4.1"] ; command line processing
                 [org.clojure/math.numeric-tower "0.0.4"] ; for a few functions
                 [mars0i/masonclj "0.2.0"]
                 [mason "19"]
                 ;; libs used by Mason:
                 [javax.media/jmf "2.1.1e"]
                 [com.lowagie/itext "1.2.3"] ; version that comes with MASON. Not in maven.org: [com.lowagie/itext "1.2"] 
                 [org.jfree/jcommon "1.0.21"]
                 [org.jfree/jfreechart "1.0.17"]
                 [org.beanshell/bsh "2.0b4"]]
  :main ^:skip-aot example.core
  :aot [example.snipe example.popenv example.Sim example.GUI example.core]
  :jvm-opts ["-Xms2g"]
  :source-paths ["src"]
  :profiles {:nogui {:main example.Sim}  ; execute this with 'lein with-profile nogui run'
             :gui   {:main example.GUI}  ; execute this with 'lein with-profile gui run'
             :core  {:main example.core}
             :uberjar {:prep-tasks [["compile" "example.GUI"]
                                    ["compile" "example.snipe"
                                     "example.Sim"
                                     "example.core"]]
                       :main example.core}} ; core decides whether to run Sim or GUI (which runs Sim)
  :target-path "target/%s"
)
