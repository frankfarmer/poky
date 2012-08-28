(ns poky.repl-helper
    (:use [clojure.java.jdbc :as sql :only [with-connection]]
          [clojure pprint walk]
          [poky db core]
          [poky.protocol memcache http]
          [lamina.core]
          [aleph.tcp]
          [gloss core io])
    ;(:import com.mchange.v2.c3p0.ComboPooledDataSource)
    (:import java.nio.ByteBuffer)
    (:import [java.lang.reflect Method]))

; create table poky ( key varchar(1024) not null, value text, constraint thekey primary key (key) );
; export DATABASE_URL=postgresql://drsnyder@localhost:5432/somedb 
(def conn (get (System/getenv) "DATABASE_URL")) 

(defn thandle [ch s] 
  (enqueue ch (format "You said %s which is a %s" s (type s))))

(defn ehandler [ch client-info]
  (receive-all ch (partial thandle ch)))

;(start-tcp-server ehandler {:port 10002, :frame (string :utf-8 :delimiters ["\r\n"])})

(def f 
  (compile-frame
    (header (string :utf-8 :delimiters " ")
            {:command (string :utf-8 :delimiters ["\r\n"])}
            first)
    (fn [b] (println "pre-encode " b))
    (fn [b] (println "post-decode " b))))

(declare buffer-to-string)
(defn frame-to-string [f]
  (apply str (concat 
               (postwalk #(if (instance? java.nio.ByteBuffer %) 
                       (buffer-to-string %)
                       %) f))))

(defn buffer-to-string [b]
  (let [cap (.capacity b)]
    (if (> cap 0)
      (loop [len (.capacity b)
             result ()]
        (if (= 0 len)
          (apply str (map #(str (char %)) result))
          (recur (dec len) (conj result (.get b (dec len))))))
      nil)))
