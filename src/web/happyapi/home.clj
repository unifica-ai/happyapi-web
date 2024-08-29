(ns web.happyapi.home
  (:require [web.happyapi.ui :as ui]
            [web.happyapi.oauth2.capture-redirect :as cr]
            [cheshire.core :as json]))

(defn home-page [ctx]
  (let [scopes ["https://www.googleapis.com/auth/drive"
                "https://www.googleapis.com/auth/drive.file"
                "https://www.googleapis.com/auth/drive.readonly"
                "https://www.googleapis.com/auth/spreadsheets"
                "https://www.googleapis.com/auth/spreadsheets.readonly"]]
    (ui/page
     ctx
     [:h1 "Welcome to your app!"]
     [:.h-4]
     [:.flex
      [:a.btn {:href (cr/login-url ctx scopes)} "Log in with Google"]]
     [:.h-2]
     [:.flex
      [:a.link {:href "/spreadsheet"} "Show spreadsheet"]])))

(defn spreadsheet [{:keys [happyapi/request] :as ctx}]
  (ui/page
   nil
   (let [spreadsheet-id "1vI2MXnZXTxOM-TEEmzIFx3z-ulz8wGZyO5Simj2vxHM"
         result (request
                 {:ctx ctx
                  :method :get,
                  :uri-template
                  "https://sheets.googleapis.com/v4/spreadsheets/{spreadsheetId}",
                  :uri-template-args {"spreadsheetId" spreadsheet-id},
                  :query-params {},
                  :scopes
                  ["https://www.googleapis.com/auth/drive"
                   "https://www.googleapis.com/auth/drive.file"
                   "https://www.googleapis.com/auth/drive.readonly"
                   "https://www.googleapis.com/auth/spreadsheets"
                   "https://www.googleapis.com/auth/spreadsheets.readonly"]})
         _ (def result* result)]
     [:h1 "Welcome to your app!"]
     [:pre
      (json/generate-string result {:pretty true})
      ])))

(def module
  {:routes [["/"  {:get home-page}]
            ["/spreadsheet" {:get spreadsheet}]]})
