(ns poky.protocol.memcache
  (:use [poky util]
        [gloss core io]))

(def CRLF "\r\n")

; :value->delimiter
(defn memcache-value->delimiter [v]
  (println (format "memcache-value->delimiter: encoding '%s'" v))
  (case v
        "END" [CRLF]
        "STORED" [CRLF]
        "DELETED" [CRLF]
        "ERROR" [CRLF]
        "CLIENT_ERROR" [CRLF]
        "SERVER_ERROR" [CRLF]
        " "))


(def CMD (string :utf-8 :delimiters [" " CRLF] 
                 :value->delimiter memcache-value->delimiter ))

(defn codec-map 
  ([] (codec-map :utf-8))
  ([charset] 
   (let [S   (string charset :delimiters [" "])
         CR  (string charset :delimiters [CRLF])]
     {:set           (compile-frame ["set" S S S CR CR])
      :add           (compile-frame ["add" S S S CR CR])
      :replace       (compile-frame ["replace" S S S CR CR])
      :stored        (compile-frame ["STORED"])
      :deleted       (compile-frame ["DELETED"])
      :get           (compile-frame ["get" CR])
      :gets          (compile-frame ["gets" CR])
      :value         (compile-frame ["VALUE" S S CR CR])
      :end           (compile-frame ["END"])
      :client_error  (compile-frame ["CLIENT_ERROR" CR])
      :server_error  (compile-frame ["SERVER_ERROR" CR])
      :error         (compile-frame ["ERROR"])})))


(defn h->b [codec hd] 
  "Called when decoding. Determines how to construct the body."
  (let [k (cmd-to-keyword hd)]
    (println (format "header h->b: '%s'" k))
    (get codec k (get codec :error))))

(defn b->h 
  "Called when encoding. Determines the header that is generated."
  [body]
  (println (format "b->h '%s'" body))
  (cmd-to-keyword (first body)))

(defn memcache-pre-encode [req] req)

(defn memcache-post-decode [res] res)

(defn memcache-codec 
  ([] (memcache-codec :utf-8))
  ([charset] 
   (compile-frame 
     (header CMD
             (partial h->b (codec-map charset))  
             b->h)
     memcache-pre-encode
     memcache-post-decode)))


