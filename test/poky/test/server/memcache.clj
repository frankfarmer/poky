(ns poky.test.server.memcache
  (:use [poky.server.memcache]
        [clojure.test]
        [midje.sweet])
  (:require [lamina.core.utils :as lamina]))

(defprotocol Queueable
             (enqueue [ch v]))

(extend-type clojure.lang.PersistentVector
             Queueable
             lamina/IEnqueue
             (enqueue [ch v] v))

(def server-set-test ["SET" "abc" "0" "0" "3" "123"])
(def server-get-test ["get" "abc def"])

(fact 
  (cmd-set-key server-set-test) => "abc")

(fact 
  (cmd-set-value server-set-test) => "123")

(fact 
  (first (cmd-gets-keys server-get-test)) => "abc")

(fact 
  (cmd-set-key server-set-test) => "abc")

(fact 
  (cmd-set-value server-set-test) => "123")

(fact
  (first (cmd-gets-keys server-get-test)) => "abc")

(fact
  (second (cmd-gets-keys server-get-test)) => "def")


; these are working as push-through unit tests via the protocol definition above.
; in practice the return value won't look like this as IEnqueue.enqueue should 
; return true if the message was enqueued to the channel

(fact 
  (cmd->dispatch "set" [] {} server-set-test 
                 (fn [cmd p] {:update true})) => ["STORED"])

(fact 
  (cmd->dispatch "set" [] {} server-set-test 
                 (fn [cmd p] {:error "something bad"})) => ["SERVER_ERROR" "something bad"])

(fact 
  (cmd->dispatch "get" [] {} server-get-test 
                 (fn [cmd p] {:values [{:key "abc" :value "123"} 
                                   {:key "def" :value "456"}]})) =>
  ["VALUE" "abc" "0" "0" "3" "123" "VALUE" "def" "0" "0" "3" "456" "END"])



