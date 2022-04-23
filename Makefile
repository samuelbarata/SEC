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

server $(id) $(total): contract shared
	cd server;\
	mvn exec:java -Dexec.args="4200 ./server$(id).ledger ./keys/$(id)/private_key.der ./server$(id).ks ./keys/$(id)/certificate.crt $(id) $(total) server$(id)"

client $(id) $(connect_to): contract shared
	cd client;\
	mvn exec:java -Dexec.args="localhost $$(expr 4200 + $(connect_to)) ./client.ks a client$(id) 0 ../server/keys/$(connect_to)/id.pub ./keys/$(id)/private_key.der ./keys/$(id)/certificate.crt"

test: 
	cd client;\
	mvn test -Dtarget=localhost:4200 -Dtest=BasicTest -DserverPublicKey=./keys/server/id.pub

test_persistence: 
	cd client;\
	mvn test -Dtarget=localhost:4200 -Dtest=PersistenceTest -DserverPublicKey=./keys/server/id.pub

test_byzantine: 
	cd client;\
	mvn test -Dtarget=localhost:4200 -Dtest=ByzantineTest -DserverPublicKey=./keys/server/id.pub

test_dos: 
	cd client;\
	mvn test -Dtarget=localhost:4200 -Dtest=DOSTest -DserverPublicKey=./keys/server/id.pub

test_crash: 
	cd client;\
	mvn test -Dtarget=localhost:4200 -Dtest=CrashTest -DserverPublicKey=./keys/server/id.pub

corrupt_ledger:
	truncate -s -10 ./server/server.ledger

delete_ledger:
	rm ./server/server.ledger

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
	echo "a" | keytool -list -keystore client.ks | grep PrivateKeyEntry

clean:
	@mvn clean
	@rm -f server/*.ledger
	@rm -f server/*.ks
	@rm -f client/*.ks
