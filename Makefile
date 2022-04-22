.PHONY: all clean test test_p contract shared server c_client c_server cert

all: contract shared cert
	@mvn compile

contract:
	@(cd contract; mvn install)

shared:
	@(cd shared; mvn install)

c_server: contract shared
	@cd server;\
	mvn compile

c_client: contract shared
	@cd client;\
	mvn compile

server: contract shared cert
	cd server;\
	mvn exec:java

client $(id): contract shared
	cd client;\
		mvn exec:java -Dexec.args="localhost 4200 ./client.ks a client$(id) 0 ./keys/server/id.pub ./keys/$(id)/private_key.der ./keys/$(id)/certificate.crt"

test: 
	cd client;\
	mvn test -Dtarget=localhost:4200 -Dtest=BasicTest -DserverPublicKey=./keys/server/id.pub

test_persistence: 
	cd client;\
	mvn test -Dtarget=localhost:4200 -Dtest=PersistenceTest -DserverPublicKey=./keys/server/id.pub

test_byzantine: 
	cd client;\
	mvn test -Dtarget=localhost:4200 -Dtest=ByzantineTest -DserverPublicKey=./keys/server/id.pub

test_crash: 
	cd client;\
	mvn test -Dtarget=localhost:4200 -Dtest=CrashTest -DserverPublicKey=./keys/server/id.pub

corrupt_ledger:
	truncate -s -10 ./server/server.ledger

delete_ledger:
	rm ./server/server.ledger

cert: server/keys/certificate.crt server/keys/privateKey.key


server/keys/certificate.crt server/keys/privateKey.key:
	cd server/keys;\
	openssl req -x509 -sha256 -nodes -days 365 -newkey rsa:2048 -keyout privateKey.key -out certificate.crt;\
	openssl pkcs8 -topk8 -inform PEM -outform DER -in privateKey.key -out private_key.der -nocrypt;\
	openssl rsa -in privateKey.key -pubout -outform DER > id.pub
	cp server/keys/id.pub client/keys/server/

checkServerKeyStore:
	@cd server;\
	echo "0" | keytool -list -keystore server.ks | grep PrivateKeyEntry

checkClientKeyStore:
	@cd client;\
	echo "0" | keytool -list -keystore client.ks | grep PrivateKeyEntry

clean:
	@mvn clean
	@rm -f server/server.ledger
	@#rm -f server/keys/certificate.crt server/keys/privateKey.key

