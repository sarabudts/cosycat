(ns cosycat.routes.users
  (:require [compojure.core :refer [routes context POST GET]]
            [cosycat.routes.utils :refer [make-default-route unwrap-arraymap]]
            [cosycat.db.users :as users]
            [cosycat.db.utils :refer [normalize-user]]
            [cosycat.app-utils :refer [query-user]]
            [cosycat.avatar :refer [user-avatar is-gravatar?]]
            [cosycat.components.ws :refer [send-clients add-active-info]]
            [cosycat.components.db :refer [colls]]
            [config.core :refer [env]]
            [taoensso.timbre :as timbre]))

;; Users info
(defn user-info-route
  [{{{username :username} :identity} :session {db :db ws :ws} :components
    {requested-username :username} :params}]
  ;; TODO: check if client has access to this user
  (->> (users/user-public-info db requested-username)
       (add-active-info ws)))

(defn remove-project-users [project-users users]
  (remove (fn [{:keys [username]}]
            (contains? (apply hash-set (map :username project-users)) username))
          users))

(defn query-users-route
  [{{{username :username} :identity} :session {{db-conn :db} :db} :components
    {value :value project-users :project-users} :params}]
  (or (->> (monger.collection/find-maps db-conn (:users colls) {})
           (remove-project-users (unwrap-arraymap project-users))
           (filter (query-user value))
           (mapv #(normalize-user % :projects :settings)))
      []))

;; Profile
(defn maybe-update-avatar
  [{new-email :email username :username {href :href} :avatar :as update-map} old-email]
  (if (and new-email (not= new-email old-email) (is-gravatar? href))
    (if-let [avatar (user-avatar username new-email)]
      (assoc update-map :avatar avatar)
      update-map)
    update-map))

(defn update-profile-route
  [{{{username :username} :identity} :session {db :db ws :ws} :components
    {update-map :update-map} :params}]
  (let [{old-email :email} (users/user-public-info db username)
        new-user-info (-> (users/update-user-info db username update-map)
                          (maybe-update-avatar old-email)
                          (normalize-user :projects :settings))]
    (send-clients
     ws {:type :new-user-info :data {:update-map new-user-info :username username}}
     :source-client username)
    new-user-info))

(defn new-avatar-route
  [{{{username :username} :identity} :session {db :db ws :ws} :components}]
  (let [{:keys [email]} (users/user-public-info db username)
        avatar (user-avatar username email)]
    (users/update-user-info db username {:avatar avatar})
    (send-clients
     ws {:type :new-user-avatar :data {:avatar avatar :username username}} :source-client username)
    avatar))

(defn users-routes []
  (routes
   (context "/users" []
     (GET "/user-info" [] (make-default-route user-info-route))
     (GET "/query-users" [] (make-default-route query-users-route))
     (POST "/update-profile" [] (make-default-route update-profile-route))
     (POST "/new-avatar" [] (make-default-route new-avatar-route)))))
