#!/bin/bash
commands=""
cd server
for (( c=0; c<${1}; c++ )) do
	commands="${commands}mvn exec:java -Dexec.args='4200 ./server${c}.ledger ./keys/${c}/private_key.der ./server.ks ./keys/${c}/certificate.crt ${c} ${1} server${c}' &"
done

eval $commands

