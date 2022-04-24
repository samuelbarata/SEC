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

 - Executing the server: `make server id=(server_id) total=(number_of_servers)`
 - Executing interactive client: `make client id=(client_id) connect_to=(server_id)`
 - Run Basic Usage Tests: `make test`
 - Run Byzantine Tests: `make test_byzantine`
 - Run Persistence Tests: `make test_persistence` (tests if server state is the expected state after basic tests and byzantine tests)
 - Run Crash Tests: `make test_crash` (assumes last line of ledger was corrupted)
 - Corrupt last line of ledger: `make corrupt_ledger`
 - Delete ledger: `make delete_ledger`
 - Run DOS Tests: `make test_dos`

The file `/server/server.ledger` will be created after the basic tests are ran.
Do note that successive test runs might fail due to the server maintaining its state; for instance, createAccount might fail because the account already exists.
Additinally, test order is important. Persistence tests will test whether the server state is as expected after running basic tests and byzantine tests. Running tests out of order will cause them to fail. 

Example Usage (where `#1` and `#2` are different terminals), showing intended test order:

```
#1                    #2
make server 0 1     |
                    | make test
                    | make test_byzantine
# Ctrl-C            |
make server         | 
                    | make test_persistence
# Ctrl-C            |
make corrupt_ledger | 
make server         | 
                    | make test_crash
                    | make test_dos
```


## Interactive Clients

Example Usage with interactive clients and multiple servers (one line per terminal):
```
make server id=0 total=2
make server id=1 total=2
make client id=0 connect_to=0
make client id=1 connect_to=1
```

Interactive client will prompt for commands. Example Usage:

Client 1:
```
Found key in keystore. Loading it...
1. Open Account
2. Send Amount
3. Receive Amount
4. Check Account
5. Audit Account
6. Exit
> 1
Response: SUCCESS
1. Open Account
2. Send Amount
3. Receive Amount
4. Check Account
5. Audit Account
6. Exit
> 2
Enter file with receiver public key
> ./keys/2/id.pub
Enter the amount to send
> 100
Response: SUCCESS
1. Open Account
2. Send Amount
3. Receive Amount
4. Check Account
5. Audit Account
6. Exit
> 4
Enter file with public key to check (empty for self)
> ./keys/2/id.pub
Response: SUCCESS
Balance: 1000
Transaction: 100 from 15zB/131ne to UEm0rlaD5G
1. Open Account
2. Send Amount
3. Receive Amount
4. Check Account
5. Audit Account
6. Exit
> 6
```

Client 2 (Must have opened account before client 1 sends amount): 
```
Found key in keystore. Loading it...
1. Open Account
2. Send Amount
3. Receive Amount
4. Check Account
5. Audit Account
6. Exit
> 1
Response: SUCCESS
1. Open Account
2. Send Amount
3. Receive Amount
4. Check Account
5. Audit Account
6. Exit
> 4
Enter file with public key to check (empty for self)
>
Response: SUCCESS
Balance: 1000
Transaction: 100 from 15zB/131ne to UEm0rlaD5G
1. Open Account
2. Send Amount
3. Receive Amount
4. Check Account
5. Audit Account
6. Exit
> 3
Enter file with sender public key
> ./keys/1/id.pub
Enter the amount to receive
> 100
Response: SUCCESS
1. Open Account
2. Send Amount
3. Receive Amount
4. Check Account
5. Audit Account
6. Exit
> 5
Enter file with public key to audit (empty for self)
>
Response: SUCCESS
Transaction: 100 from 15zB/131ne to UEm0rlaD5G
1. Open Account
2. Send Amount
3. Receive Amount
4. Check Account
5. Audit Account
6. Exit
> 5
Enter file with public key to audit (empty for self)
> ./keys/1/id.pub
Response: SUCCESS
Transaction: 100 from 15zB/131ne to UEm0rlaD5G
1. Open Account
2. Send Amount
3. Receive Amount
4. Check Account
5. Audit Account
6. Exit
> 6
```