(defproject example "0.1.0-SNAPSHOT"
  :description "Example of use of masonclj in agent-based simulation using MASON"
  :url "http://example.com/FIXME"
  :license {:name "LGPL 3.0"
            :url "https://www.gnu.org/licenses/lgpl.html"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/tools.cli "0.4.1"]
                 [mars0i/masonclj "0.1.0"]
                 [mason "19"]]
  :main ^:skip-aot example.core
  :aot [pasta.mush pasta.snipe pasta.popenv pasta.Sim pasta.UI pasta.core]
  :jvm-opts ["-Xms2g"]
  :source-paths ["src/clj"]
  :profiles {:nogui {:main example.Sim} ; execute this with 'lein with-profile nogui run'
             :gui   {:main example.UI}      ; execute this with 'lein with-profile gui run'
             :core  {:main example.core}
             :uberjar {:prep-tasks [["compile" "example.UI"]
                                    ["compile" "example.snipe"
                                     "example.Sim"
                                     "example.core"]]
                       :main pasta.core}}
  :target-path "target/%s"
)
