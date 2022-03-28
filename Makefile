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

test: contract shared
	cd client;\
	mvn test -Dtarget=localhost:8080 -Dtest=BasicTest -DserverPublicKey=./keys/server/id.pub

test_p: contract shared
	cd client;\
	mvn test -Dtarget=localhost:8080 -Dtest=PersistenceTest -DserverPublicKey=./keys/server/id.pub

clean:
	@mvn clean
	@rm -f server/server.ledger