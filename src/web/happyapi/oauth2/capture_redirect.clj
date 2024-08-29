(ns web.happyapi.oauth2.capture-redirect
  (:require
   [clojure.set :as set]
   [happyapi.middleware :as middleware]
   [happyapi.oauth2.auth :as oauth2]
   [happyapi.oauth2.client :as client]
   [web.happyapi.oauth2.credentials :as credentials]))

;; TODO use Temporal for state and challenge
(defonce state-and-challenge (str (random-uuid)))

(defn login-url [{:keys [happyapi/config] :as ctx} additional-scopes]
  (let [config (client/with-endpoints config)
        {:keys [scopes authorization_options code_challenge_method]} config
        scopes (->> (concat scopes additional-scopes) (into []))
        optional (merge authorization_options
                        {:state state-and-challenge}
                        (when code_challenge_method
                          {:code_challenge state-and-challenge}))]
    (oauth2/provider-login-url config scopes optional)))

(defn exchange-code
  "Step 5: Exchange authorization code for refresh and access tokens.
  When the user is redirected back to your app from Google with a short-lived code,
  exchange the code for a long-lived access token."
  [request
   {:as config :keys [token_uri client_id client_secret redirect_uri]}
   code
   code_verifier]
  (let [resp (request {:method          :post
                       :url             token_uri
                       ;; Google documentation says client_id and client_secret should be parameters,
                       ;; but accepts them in the Basic Auth header (undocumented).
                       ;; Other providers require them as Basic Auth header.
                       :headers         {"Authorization" (str "Basic " (oauth2/base64 (str client_id ":" client_secret)))}
                       ;; params can be sent as form-params or json body
                       :body            (cond-> {:code         code
                                                 :grant_type   "authorization_code"
                                                 :redirect_uri redirect_uri}
                                                code_verifier (assoc :code_verifier code_verifier))
                       :keywordize-keys true})]
    (oauth2/with-timestamp resp)

    ;; This seems to be returning the response body only, not the outer HTTP object?
    ;; Not sure why.
    ;;
    #_(oauth2/with-timestamp (:body resp))
    #_(when (middleware/success? resp)
      (oauth2/with-timestamp (:body resp)))))

(defn handler
  [{:keys [happyapi/request happyapi/config query-params] :as ctx}]
  (def ctx* ctx)
  (let [config (client/with-endpoints config)
        {:strs [code state]}            query-params
        {:keys [authorization_options]} config
        {:keys [code_challenge_method]} authorization_options

        optional (merge authorization_options
                        {:state state-and-challenge}
                        (when code_challenge_method
                          {:code_challenge state-and-challenge}))

        tokens (delay (exchange-code request config code
                                            (when code_challenge_method state-and-challenge)))]
    (if code
      (do
        (when-not (= state state-and-challenge)
          (throw (ex-info "Redirected state does not match request"
                          {:id            ::redirected-state-mismatch
                           :optional      optional
                           :return-params query-params})))
        (credentials/save-credentials ctx nil "user" @tokens)
        {:status 303
         :headers {"location" "/"}})
      {:status 400
       :body   "No code in response."})))

(defn update-credentials
  "Use credentials if valid, refresh if necessary.
  For valid optional params, see https://developers.google.com/identity/protocols/oauth2/web-server#httprest_1"
  ([request config credentials scopes]
   ;; scopes can grow
   (let [scopes (set/union (oauth2/credential-scopes credentials) (set scopes))]
     ;; merge to retain refresh token
     (def credentials* credentials)
     (def scopes* scopes)
     (merge credentials
            (or
             ;; already have valid credentials
             (and (oauth2/valid? credentials)
                  (oauth2/has-scopes? credentials scopes)
                  credentials)
             ;; try to refresh existing credentials
             (and (oauth2/refreshable? config credentials)
                  (oauth2/has-scopes? credentials scopes)
                  (oauth2/refresh-credentials (middleware/wrap-keywordize-keys request true) config scopes credentials))
             ;; new credentials required
             #_(fresh-credentials (middleware/wrap-keywordize-keys request true) config scopes))))))

(comment
  (defn valid? [{:as credentials :keys [expires_at access_token]}]
    (boolean
     (and access_token
          (or (not expires_at)
              (neg? (.compareTo (Date.) expires_at))))))
  )

(def schema
  {:auth/id :uuid
   :auth
   [:map {:closed true}
    [:xt/id :auth/id]
    [:auth/user :string]
    [:auth/access_token :string]
    [:auth/expires_in :int]
    [:auth/refresh_token :string]
    [:auth/scope :string]
    [:auth/token_type :string]
    [:auth/items vector?]
    [:auth/expires_at inst?]]})

(def module
  {:schema schema
   :routes [["/redirect" {:get handler}]]})
