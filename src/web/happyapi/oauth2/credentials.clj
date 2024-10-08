(ns web.happyapi.oauth2.credentials
  (:require
    [com.biffweb :as biff]))

(defn- ns-keys
  "Namespace-qualifies map keys"
  [m n]
  (reduce-kv (fn [acc k v]
               (let [new-kw (if (and (keyword? k)
                                     (not (qualified-keyword? k)))
                              (keyword (str n) (name k))
                              k) ]
                 (assoc acc new-kw v)))
             {} m))

(defn- un-ns-keys
  "Remove namespace qualification"
  [m]
  (update-keys m (comp keyword name)))

(defn load-credentials [{:keys [biff/db]} provider user]
  (-> (biff/lookup db :auth/user user)
      (dissoc :xt/id)
      un-ns-keys))

(defn save-credentials [ctx provider user credentials]
  (when credentials
    (biff/submit-tx ctx [(-> credentials
                             (assoc :user user)
                             (ns-keys "auth")
                             (assoc :db/doc-type :auth))])))
