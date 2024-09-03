(ns web.happyapi.oauth2.client
  (:require
   [clojure.string :as str]
   [com.biffweb :as biff]
   [happyapi.middleware :as middleware]
   [happyapi.oauth2.client :as oauth2.client]
   [web.happyapi.oauth2.capture-redirect :as capture-redirect]
   [web.happyapi.oauth2.credentials :as credentials]))

;; Perhaps this could be one of the fns?
(defn oauth2
  "Performs a http-request that includes oauth2 credentials.
  Refreshes but does not reload using the Web flow.
  See https://developers.google.com/identity/protocols/oauth2 for more information."
  [request args config]
  (let [{:keys [provider]} config
        {:keys [ctx user scopes] :or {user "user" scopes (:scopes config)}} args
        credentials (credentials/load-credentials ctx provider user)
        credentials (capture-redirect/update-credentials request config credentials scopes)
        {:keys [access_token]} credentials]
    #_(credentials/save-credentials ctx provider user credentials)
    (if access_token
      (request (middleware/auth-header args access_token))
      (request args)
      #_(throw (ex-info (str "Failed to obtain credentials for " user)
                      {:id     ::failed-credentials
                       :user   user
                       :scopes scopes})))))

(defn wrap-oauth2
  "Wraps a http-request function that uses keys user and scopes from args to authorize according to config."
  [request config]
  (let [config (oauth2.client/with-endpoints config)]
    (when-let [ks (oauth2.client/missing-config config)]
      (throw (ex-info (str "Invalid config: missing " (str/join "," ks))
                      {:id      ::invalid-config
                       :missing (vec ks)
                       :config  config})))
    (when-not (middleware/fn-or-var? request)
      (throw (ex-info "request must be a function or var"
                      {:id           ::request-must-be-a-function
                       :request      request
                       :request-type (type request)})))
    (let [query-string (get-in config [:fns :query-string])]
      (when-not (middleware/fn-or-var? query-string)
        (throw (ex-info "query-string must be provided in config :fns :query-string as a function or var"
                        {:id           ::query-string-must-be-a-function
                         :query-string query-string
                         :config       config}))))
    (let [run-server (get-in config [:fns :run-server])]
      (when-not (middleware/fn-or-var? run-server)
        (throw (ex-info "run-server must be provided in config :fns :run-server as a function or var"
                        {:id         ::run-server-must-be-a-function
                         :run-server run-server
                         :config     config}))))
    (fn [args]
      (oauth2 request args config))))

(defn wrap-debug-middlware [request]
  (fn
    ([args]
     (let [_ (println "--------------- REQUEST ----------------------")
           _ (biff/pprint args)
           _ (def args* args)
           response (request (assoc args :debug-body true))]
       (println "----------------- RESPONSE ------------------")
       (biff/pprint response)
       (def response* response)
       response))
    ([args respond raise]
     (request args respond raise))))

(defn make-client
  "Given a config map

  {#{:client_id :client_secret}  <string>
   :provider                     #{:google :amazon :github :twitter ...}
   #{:auth_uri :token_uri}       <string>
   :fns                          {#{:request :query-string :encode :decode} <fn-or-var>}
   :keywordize-keys              <boolean>}

  Returns a wrapped request function.

  If `provider` is known, then auth_uri and token_uri endpoints will be added to the config,
  otherwise expects `auth_uri` and `token_uri`.
  `provider` is required to namespace tokens, but is not restricted to known providers.
  Dependencies are passed as functions in `fns`."
  [ctx {:as config :keys [keywordize-keys] {:keys [request]} :fns}]
  (def ctx* ctx)
  (when-not (middleware/fn-or-var? request)
    (throw (ex-info "request must be a function or var"
                    {:id      ::request-must-be-a-function
                     :request request
                     :config  config})))
  (let [ctx-keys [:biff/db
                  :biff.xtdb/node
                  :biff.xtdb/retry]
        assoc-db (fn [args] (assoc args :ctx (select-keys ctx ctx-keys)))
        wrap-db  (fn [request]
                  (fn
                    ([args] (request (assoc-db args)))
                    ([args respond raise] (request (assoc-db args) respond raise))))]
    (-> request
        (wrap-debug-middlware)
        (middleware/wrap-cookie-policy-standard)
        (middleware/wrap-informative-exceptions)
        (middleware/wrap-json config)
        (wrap-oauth2 config)
        wrap-db
        (middleware/wrap-uri-template)
        (middleware/wrap-paging)
        (middleware/wrap-extract-result)
        (middleware/wrap-keywordize-keys keywordize-keys))))
