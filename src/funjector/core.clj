(ns funjector.core
  (:use clojopts.core
        clojure.pprint)
  (:import java.io.ByteArrayOutputStream)
  (:require [clj-http.client :as http]
            [clojure.string :as st]
            [clojure.data.codec.base64 :as b64]
            [clojure.java.io :as io])
  (:gen-class))

(defn erl-format [xpr]
  (str "list_to_binary(io_lib:format(\"~w\",[" xpr "]))"))

(defn load-expr [filename]
  (let [beamfile (io/file filename)
        basename (st/replace (.getName beamfile) ".beam" "")
        baos (ByteArrayOutputStream.)]
    (io/copy beamfile baos)
    (erl-format (str "code:load_binary(" basename ",\"" (.getName beamfile) 
                     "\",base64:decode(<<\""
                     (String. (b64/encode (.toByteArray baos))) "\">>))"))))

(defn load-files [opts]
  (when-let [files (seq (:clojopts/more opts))]
    (let [{:keys [username password node]} opts
          nodeuri (java.net.URI. node)
          results (:body (http/post (str (.resolve nodeuri "/diag/eval"))
                                    {:basic-auth [username password]
                                     :as :json
                                     :body (str "{json,[" (st/join "," (map load-expr files)) "]}.")}))]
      (doseq [[file result] (map vector files results)]
        (println file "-" result)))))

(defn -main
  [& args]
  (let [opts (merge
               {:node "http://127.0.0.1:8091/" 
                :username "Administrator"
                :password "password"}
               (clojopts "funjector" args
                         (optional-arg node N "Couchbase node (default: http://127.0.0.1:8091/)")
                         (optional-arg username u "Couchbase username (default: Administrator)")
                         (optional-arg password p "Couchbase password (default: password)")))]
    (try (load-files opts)
      (catch clojure.lang.ExceptionInfo e
        (pprint (select-keys (:object (ex-data e)) [:status :headers :body]))))))
