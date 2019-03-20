(defproject example "0.1.0-SNAPSHOT"
  :description "Example of use of masonclj in agent-based simulation using MASON"
  :url "http://example.com/FIXME"
  :license {:name "LGPL 3.0"
            :url "https://www.gnu.org/licenses/lgpl.html"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/tools.cli "0.4.1"]
                 [mars0i/masonclj "0.1.0"]
                 [mason "19"]
                 ;; libs used by Mason:
                 [javax.media/jmf "2.1.1e"]
                 [com.lowagie/itext "1.2.3"] ; version that comes with MASON. Not in maven.org: [com.lowagie/itext "1.2"] 
                 [org.jfree/jcommon "1.0.21"]
                 [org.jfree/jfreechart "1.0.17"]
                 [org.beanshell/bsh "2.0b4"]
                 
                 ;; FIXME TEMPORARY just to get it to compile while stripping down pasta code:
                 [org.clojure/math.numeric-tower "0.0.4"]
                 [org.clojure/tools.cli "0.4.1"]
                 [org.clojure/data.csv "0.1.3"]
                 [org.clojure/algo.generic "0.1.2"]
                 [com.rpl/specter "1.0.0"]
                 
                ]
  :main ^:skip-aot example.core
  :aot [example.mush example.snipe example.popenv example.Sim example.UI example.core]
  :jvm-opts ["-Xms2g"]
  :source-paths ["src"]
  :profiles {:nogui {:main example.Sim} ; execute this with 'lein with-profile nogui run'
             :gui   {:main example.UI}      ; execute this with 'lein with-profile gui run'
             :core  {:main example.core}
             :uberjar {:prep-tasks [["compile" "example.UI"]
                                    ["compile" "example.snipe"
                                     "example.Sim"
                                     "example.core"]]
                       :main example.core}}
  :target-path "target/%s"
)
