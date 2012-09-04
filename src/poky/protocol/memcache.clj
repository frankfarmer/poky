(ns poky.protocol.memcache
  (:use [poky.core :as poky]
        [lamina.core]
        [aleph.tcp]
        [gloss core io]))


; :value->delimiter
(defn memcache-value->delimiter [v]
  (println (format "memcache-value->delimiter: encoding '%s'" v))
  (case v
        "END" ["\r\n"]
        "STORED" ["\r\n"]
        " "))


(def C (string :utf-8 :delimiters [" "]))
(def K (string :utf-8 :delimiters [" "]))
(def V (string :utf-8 :delimiters [" "]))
(def CMD (string :utf-8 :delimiters [" " "\r\n"] :value->delimiter memcache-value->delimiter ))
(def CR (string :utf-8 :delimiters ["\r\n"]))

;set <key> <flags> <exptime> <bytes> [noreply]\r\n<data block>\r\n
;set <key> <flags> <exptime> <bytes>\r\n<data block>\r\n
(defcodec SET ["set" C C C CR CR])

;STORED\r\n
(defcodec STORED ["STORED"])

;get <key>*\r\n
;gets <key>*\r\n

; this doesn't work as expected (for this protocol), because the last repeated element will also be
; delimited. 
(defcodec KEYS (repeated (string :utf-8 :delimiters [" "]) 
                         :delimiters ["\r\n" " "]))

(defcodec GET ["get" CR])
(defcodec GETS ["gets" CR])

(defcodec VALUE ["VALUE" K V V CR CR])
;VALUE <key> <flags> <bytes> [<cas unique>]\r\n
;<data block>\r\n
;...

(defcodec END ["END"])
;END\r\n

(defcodec ERRC CR)


(defn body->h [body] 
  (println (format "body->h: '%s' '%s'" body (first body)))
  (first body))



(defn h->b [hd] 
  "Called when decoding. Determines how to construct the body."
  (println (format "header h->b: '%s'" hd))
    (case hd
      "gets" GETS
      "get"  GET
      "set" SET
      "VALUE" VALUE
      "STORED" STORED
      "END" END
          ERRC))



(defn b->h 
  "Called when encoding. Determines the header that is generated."
  [body]
  (println (format "b->h '%s'" body))
  (first body))

(defn memcache-pre-encode [req]
  (println (format "memcache-pre-encode '%s'" req))
  req)

(defn memcache-post-decode [res]
  (println (format "memcache-post-decode '%s'" res))
  res)

(defcodec MEMCACHE (compile-frame 
                     (header CMD h->b b->h)
                     memcache-pre-encode
                     memcache-post-decode))


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
  (nth decoded 5))

(defn cmd-gets-keys [decoded]
  (clojure.string/split (first (rest decoded)) #" "))

(defn debug-response [ch msg r]
  (do 
    (println msg r)
    (map #(enqueue ch %) r)))

(defn test-handle [ch ci cmd]
    (println "Processing command: " (first cmd) " from " ci)
    (condp = (first cmd)
      "SET" (enqueue ch ["STORED"]) 
      "GET" (do (enqueue ch ["VALUE" "abc" "123"]) (enqueue ch ["END" ""]))
      "GETS" (do (enqueue ch ["VALUE" "abc" "123"]) (enqueue ch ["END" ""]))
      (enqueue ch "error")))


(defn handler
  [ch ci]
  (receive-all ch (partial test-handle ch ci)))


;(def s (start-tcp-server handler {:port 10000 :frame MEMCACHE}))