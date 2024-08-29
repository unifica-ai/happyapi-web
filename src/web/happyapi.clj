(ns web.happyapi
  (:require [com.biffweb :as biff]
            [web.happyapi.home :as home]
            [web.happyapi.middleware :as mid]
            [web.happyapi.ui :as ui]
            [web.happyapi.oauth2.capture-redirect :as oauth2.capture-redirect]
            [web.happyapi.schema :as schema]
            [clojure.test :as test]
            [clojure.tools.logging :as log]
            [clojure.tools.namespace.repl :as tn-repl]
            [malli.core :as malc]
            [malli.registry :as malr]
            [nrepl.cmdline :as nrepl-cmd]
            [web.happyapi.setup :as happyapi])
  (:gen-class))

(def modules
  [home/module
   schema/module
   oauth2.capture-redirect/module])

(def routes [["" {:middleware [mid/wrap-site-defaults]}
              (keep :routes modules)]
             ["" {:middleware [mid/wrap-api-defaults]}
              (keep :api-routes modules)]])

(def handler (-> (biff/reitit-handler {:routes routes})
                 mid/wrap-base-defaults))

(def static-pages (apply biff/safe-merge (map :static modules)))

(defn generate-assets! [ctx]
  (biff/export-rum static-pages "target/resources/public")
  (biff/delete-old-files {:dir "target/resources/public"
                          :exts [".html"]}))

(defn on-save [ctx]
  (biff/add-libs)
  (biff/eval-files! ctx)
  (generate-assets! ctx)
  (test/run-all-tests #"happyapi.*-test"))

(def malli-opts
  {:registry (malr/composite-registry
              malc/default-registry
              (apply biff/safe-merge (keep :schema modules)))})

;; TODO use Aero to load config
(defn use-happyapi [ctx]
  (let [config (happyapi/load-config nil :google)]
    (assoc ctx :happyapi/config  config)))

(defn assoc-happyapi-request
  "Load happyapi request into context"
  [{:keys [happyapi/config] :as ctx}]
  (let [client (happyapi/make-client ctx config)]
    (assoc ctx :happyapi/request client)))

(def merge-context-fn
  (comp assoc-happyapi-request biff/assoc-db))

(def initial-system
  {:biff/merge-context-fn merge-context-fn
   :biff/modules #'modules
   :biff/handler #'handler
   :biff/malli-opts #'malli-opts
   :biff.beholder/on-save #'on-save
   :biff.middleware/on-error #'ui/on-error
   :biff.xtdb/tx-fns biff/tx-fns})

(defonce system (atom {}))

(def components
  [biff/use-aero-config
   use-happyapi
   biff/use-xtdb
   biff/use-queues
   biff/use-xtdb-tx-listener
   biff/use-htmx-refresh
   biff/use-jetty
   biff/use-beholder])

(defn start []
  (let [new-system (reduce (fn [system component]
                             (log/info "starting:" (str component))
                             (component system))
                           initial-system
                           components)]
    (reset! system new-system)
    (generate-assets! new-system)
    (log/info "System started.")
    (log/info "Go to" (:biff/base-url new-system))
    new-system))

(defn -main []
  (let [{:keys [biff.nrepl/args]} (start)]
    (apply nrepl-cmd/-main args)))

(defn refresh []
  (doseq [f (:biff/stop @system)]
    (log/info "stopping:" (str f))
    (f))
  (tn-repl/refresh :after `start)
  :done)
