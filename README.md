# BftBank
###### Fábio Sousa 93577 -  Pedro Godinho 93608 - Samuel Barata 94230

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
 - Run Byzantine Tests: `make test_byzantine`
 - Run Persistence Tests: `make test_persistence` (tests if server state is the expected state after basic tests and byzantine tests)
 - Run Crash Tests: `make test_crash` (assumes last line of ledger was corrupted)
 - Corrupt last line of ledger: `make corrupt_ledger`
 - Delete ledger: `make delete_ledger`

The file `/server/server.ledger` will be created after the basic tests are ran.
Do note that successive test runs might fail due to the server maintaining its state; for instance, createAccount might fail because the account already exists.
Additinally, test order is important. Persistence tests will test whether the server state is as expected after running basic tests and byzantine tests. Running tests out of order will cause them to fail. 

Example Usage (where `#1` and `#2` are different terminals), showing intended test order:

```
#1                    #2
make server         |
                    | make test
                    | make test_byzantine
# Ctrl-C            |
make server         | 
                    | make test_persistence
# Ctrl-C            |
make corrupt_ledger | 
make server         | 
                    | make test_crash
```
