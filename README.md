# BftBank

This is a gRPC application, composed of four modules:
- [contract](contract/) - protocol buffers definition
- [shared](shared/) - library for shared code
- [server](server/) - implementation of service
- [client](client/) - invocation of service

----

## Running the Project

The project includes a makefile to simplify execution and testing:

 - Executing the server: `make server`
 - Run Basic Usage Tests: `make test`
 - Run Persistence Tests: `make test_p`

The file `/server/server.ledger` will be created after the basic tests are ran.
The persistence test checks if the bank state is correct after the basic usage tests.
The server is intended to be stopped and restarted after basic usage tests.