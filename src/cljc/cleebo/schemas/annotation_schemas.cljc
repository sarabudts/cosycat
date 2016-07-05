(ns cleebo.schemas.annotation-schemas
  (:require [schema.core :as s]
            [schema.coerce :as coerce]
            [taoensso.timbre :as timbre]
            #?(:clj [clojure.core.match :refer [match]]
               :cljs [cljs.core.match :refer-macros [match]])))


(def cpos-schema s/Int)

(def history-schema
  [{:ann {:key s/Str
          :value s/Str}
    :username s/Str
    :project s/Str
    :timestamp s/Int}])

(def token-span-schema
  {:type (s/enum "token")
   :scope cpos-schema})

(def iob-span-schema
  {:type (s/enum "IOB")
   :scope {:B cpos-schema
           :O cpos-schema}})

(def span-schema
  (s/conditional #(= (:type %) "token") token-span-schema
                 #(= (:type %) "IOB")   iob-span-schema))

(def annotation-schema
  {:_id s/Any                            ;version-overarching unique identifier
   :ann {:key s/Str :value s/Str}
   :username s/Str
   :timestamp s/Int
   :span span-schema
   :project s/Str
   (s/optional-key :history) history-schema})

(def cpos-anns-schema
  {:anns [{:key s/Str :ann-id s/Int}]
   (s/optional-key :_id) cpos-schema})


