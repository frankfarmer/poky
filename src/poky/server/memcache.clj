(ns poky.server.memcache
  (:use [poky util]
        [lamina.core]
        [aleph.tcp])
  (:require [poky.protocol.memcache :as pm]
            [poky.core :as poky]))

(defn cmd-args-len [decoded]
  (count (rest decoded)))

(defn cmd-args [decoded n]
  (nth decoded n))

(defn extract-cmd-args [decoded f]
  (let [args (clojure.string/split (cmd-args decoded) #" ")]
    (if args
      (f args)
      nil)))

(defn cmd-set-key [decoded]
  (second decoded))

(defn cmd-set-value [decoded]
  (nth decoded 4))

(defn cmd-gets-keys [decoded]
  (clojure.string/split (first (rest decoded)) #" "))

(defn cmd-delete-key [decoded]
  (second decoded))


(defn enqueue-tuples [ch tuples]
  (doall 
    (map #(enqueue ch %)
       (map (fn [t] ["VALUE" (:key t) "0" (:value t)]) tuples))))


(defmulti cmd->dispatch
  (fn [cmd channel client-info payload process-fn] (cmd-to-keyword cmd)))


(defmethod cmd->dispatch :set
  [cmd channel client-info payload process-fn] 
  (let [response (process-fn cmd payload)]
    (cond
      (or (:update response) (:insert response)) (enqueue channel ["STORED"])
      (:error response) (enqueue channel ["SERVER_ERROR" (:error response)])
      :else (enqueue channel ["SERVER_ERROR" "oops, something bad happened while setting."]))))

(defmethod cmd->dispatch :get
  [cmd channel client-info payload process-fn] 
  (let [response (process-fn cmd payload)]
    (cond 
      (:values response)
      (if (> (count (:values response)) 0)
        (do 
          (enqueue-tuples channel (:values response))
          (enqueue channel ["END"]))
        (enqueue channel ["END"]))
      (:error response) (enqueue channel ["SERVER_ERROR" (:error response)])
      :else (enqueue channel ["SERVER_ERROR" "oops, something bad happened while getting."]))))

(defmethod cmd->dispatch :gets
  [cmd channel client-info payload process-fn] 
  (cmd->dispatch :get channel client-info payload process-fn))

(defmethod cmd->dispatch :delete
  [cmd channel client-info payload process-fn] 
  (let [response (process-fn cmd payload)]
    (cond 
      (:deleted response) (enqueue channel ["DELETED"])
      (:error response) (enqueue channel ["SERVER_ERROR" (:error response)])
      :else (enqueue channel ["SERVER_ERROR" "oops, something bad happened while deleting."]))))


; is this a client error or just error?
(defmethod cmd->dispatch :default
  [cmd channel client-info payload process-fn] 
  (enqueue channel ["ERROR"]))



(defmulti storage->dispatch
  (fn [cmd req] (cmd-to-keyword cmd)))

(defmethod storage->dispatch :set
  [cmd req] 
  {:pre [(= (count req) 5)]}
  (poky/add 
    (cmd-set-key req)
    (cmd-set-value req)))

(defmethod storage->dispatch :get
  [cmd req]
  {:pre [(= (count req) 2)]}
  (poky/gets (cmd-gets-keys req)))

(defmethod storage->dispatch :gets
  [cmd req]
  {:pre [(= (count req) 2)]}
  (storage->dispatch :get req))

(defmethod storage->dispatch :delete
  [cmd req]
  {:pre [(= (count req) 2)]}
  (poky/delete (cmd-delete-key req)))

(defmethod storage->dispatch :default
  [cmd req]
  {:error (format "Unknown storage command %s." cmd)})


(defn memcache-handler [ch ci cmd]
  (let [cmd-key (first cmd)]
    (cmd->dispatch cmd-key ch ci cmd
                   storage->dispatch)))

(defn handler
  [ch ci]
  (receive-all 
    ch (partial memcache-handler ch ci)))

(defn start-server [port]
  (start-tcp-server handler {:port port :frame (pm/memcache-codec :utf-8)}))
