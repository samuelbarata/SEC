.PHONY: all clean test test_p contract shared server c_client c_server

all: contract shared
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

server: contract shared
	cd server;\
	mvn exec:java

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

clean:
	@mvn clean
	@rm -f server/server.ledger
