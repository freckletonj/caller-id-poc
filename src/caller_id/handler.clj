(ns caller-id.handler
  (:gen-class)
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.set :refer [index]]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.adapter.jetty :as ring]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [ring.middleware.json :refer [wrap-json-response wrap-json-body]]
            [ring.util.response :refer [response header]]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Config

(def csv-path "resources/interview-callerid-data.csv")


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utilities

(defn read-csv
  "read in a path and convert it to csv"
  [path]
  (with-open [in (io/reader path)]
    (doall (csv/read-csv in))))


;; Phone Numbers ----------

(defn correct-phone-chars
  "predicate that returns true if a string contains the appropriate
  characters to be a phone number"
  [s]
  (re-matches #"[\d\+\-\(\) ]{10,20}" s))

(defn is-e164-phone?
  "predicate that returns true if a string matches the E.164 spec"
  [s]
  (re-matches #"^\+1[0-9]{10}$" s))

(defn coerce-e164
  "try to coerce an alleged phone number to E.164, and throw an error
  if it cannot be done"
  [s]
  {:pre  [(correct-phone-chars s)]
   :post [(is-e164-phone? %)]}
  (as-> s $
       (clojure.string/replace $ #"\+1" "")
       (re-seq (re-pattern "\\d+") $)
       (apply concat $)
       (apply str "+1" $)))


;; Data Munging ----------

(defn to-entry
  "convert a vector of keys and a vector of values to a map

  (to-entry [:a :b :c] [:x :y :z]) => {:a :x, :b :y, :c :z}"
  [ks vs]
  (->> (interleave ks vs)
       (partition 2) 
       (reduce (fn [c [k v]]
                 (assoc c k v)) {})))

(defn to-entries [ks es]
  (map (partial to-entry ks) es))

(defn index-phone-entries [es]
  (reduce (fn [c e]
            (update c (:phone e) #(assoc % (:context e) (:name e)) )) {} es))

(defn munge-phones
  "turn a csv of [[<phone> <context> <name>]] into a map indexed by
  phone number then context, eg:

  {<phone> {<context1> <name>, <context2> <name>}, ...}"
  [es]
  (as-> es $
    (to-entries [:phone :context :name] $)
    (map #(update % :phone coerce-e164) $)
    (index-phone-entries $)))


;; Boundary Functions ----------

(defn by-phone
  "boundary function for getting all entries for a given number"
  [phone db-atom]
  (map (fn [[context name]]
         {:phone phone
          :context context
          :name name}) (get @db-atom phone)))

(defn add-entry
  "add entry to the db-atom. Uses a silently overwriting policy when
  there is a duplicate phone+context"
  [{:keys [phone context name]} db-atom]
  (do (swap! db-atom (fn [db] (update db phone
                                      (fn [row] (assoc row context name)))))
      nil))


;; TODO: add these to tests ----------
(comment
  (coerce-e164 "123-456-7890")          ; => +11234567890
  (coerce-e164 "+14027491758")          ; => +14027491758
  (coerce-e164 "(440) 243-3400")        ; => +14402433400
  (coerce-e164 "+1402749")              ; => should fail correct-phone-chars
  
  (to-entry [:a :b :c] [:x :y :z])      ; => {:a :x, :b :y, :c :z}

  (by-phone "+14027491758" state)
  (add-entry {:phone "+14027491758"
              :context "plumbing"
              :name "Mario"} state)) 


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; App State
;;
;; Recommendations:
;;
;; Component would work well if the state grew beyond this PoC
;; condition
;;
;; Specter would be useful, again, if this grew much
;;
;; Also, I would recomment composite keys and persistence here,
;; perhaps even just sqlite

(def state (atom (munge-phones (read-csv csv-path)))) ; TODO: this
                                                      ; takes ~6.05s
                                                      ; to load into
                                                      ; memory, make
                                                      ; efficient


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Endpoints
(defn json-header [r] (header r "Content-Type" "application/json"))
(defroutes app-routes
  (GET "/query" [number]
       (try
         (let [formatted-n (coerce-e164 number)
               es          (by-phone formatted-n state)]
           (if (empty? es)
             ;; None found
             (-> (response {:results []})
                 (json-header)
                 (assoc :status 404))

             ;; Success!
             (-> (response {:results es})
                 (json-header))))
         (catch AssertionError e

           ;; Malformed Request
           (-> (response {:error (str e)})
               (assoc :status 400)))))
  
  (POST "/number/:num" {params :params body :body}
        (try
          (do
            (assert (= (set (keys body))
                       (set [:phone :context :name])))

            ;; Add the new entry, overwrite conflicts
            (add-entry (update body :phone coerce-e164) state)

            ;; Success
            (response {:response "success"}))
          (catch AssertionError e

            ;; Malformed Request
            (-> (response {:error (str e)})
                (assoc :status 400)))))
  (route/not-found "Not Found"))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Entry

(def app
  (-> app-routes
      (wrap-json-body {:keywords? true})
      (wrap-defaults api-defaults)
      (wrap-json-response)))

(defn -main []
  (let [port (Integer. (or (System/getenv "PORT") "3000"))]
    (ring/run-jetty app {:port  port
                         :join? false}  )))
