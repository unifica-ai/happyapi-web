(ns web.happyapi.setup
  (:require [happyapi.setup :as happyapi]
            [web.happyapi.oauth2.client :as oauth2.client]
            [happyapi.apikey.client :as apikey.client]))

(defn load-config
  "If `config` is nil, will attempt to find edn config in the environment HAPPYAPI_CONFIG,
  or a file happyapi.edn

  If specified, `config` should be a map:

      {:google {:deps            [:clj-http :cheshire]  ;; see happyapi.deps for alternatives
                :fns             {...}                  ;; if you prefer to provide your own dependencies
                :client_id       \"MY_ID\"              ;; oauth2 client_id of your app
                :client_secret   \"MY_SECRET\"          ;; oauth2 client_secret from your provider
                :apikey          \"MY_API_KEY\"         ;; only when not using oauth2
                :scopes          []                     ;; optional default scopes used when none present in requests
                :keywordize-keys false                  ;; optional, defaults to true
                :provider        :google}}              ;; optional, use another provider urls and settings

  The `provider` argument is required and should match a top level key to use (other configs may be present)."
  [config provider]
  (when-not provider
    (throw (ex-info "Provider is required"
                    {:id       ::provider-required
                     :provider provider
                     :config   config})))
  (let [config (if (nil? config)
                 (happyapi/find-config)
                 (happyapi/as-map config))
        config (-> (get config provider)
                   (update :provider #(or % provider)))
        {:keys [client_id apikey]} config]
    (-> (happyapi/with-deps config) (happyapi/resolve-fns))))


(defn make-client
  "Returns a function that can make requests to an api.

  If `config` is nil, will attempt to find edn config in the environment HAPPYAPI_CONFIG,
  or a file happyapi.edn

  If specified, `config` should be a map:

      {:google {:deps            [:clj-http :cheshire]  ;; see happyapi.deps for alternatives
                :fns             {...}                  ;; if you prefer to provide your own dependencies
                :client_id       \"MY_ID\"              ;; oauth2 client_id of your app
                :client_secret   \"MY_SECRET\"          ;; oauth2 client_secret from your provider
                :apikey          \"MY_API_KEY\"         ;; only when not using oauth2
                :scopes          []                     ;; optional default scopes used when none present in requests
                :keywordize-keys false                  ;; optional, defaults to true
                :provider        :google}}              ;; optional, use another provider urls and settings

  The `provider` argument is required and should match a top level key to use (other configs may be present)."
  [ctx {:keys [client_id apikey] :as config-with-fns}]
  (cond client_id (oauth2.client/make-client ctx config-with-fns)
        apikey (apikey.client/make-client config-with-fns)
          :else (throw (ex-info "Missing config, expected `:client_id` or `:apikey`"
                                {:id     ::missing-config}))))
