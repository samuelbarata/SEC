# gRPC server

This is the gRPC server application.

The server depends on the contract module, where the protocol buffers shared between server and client are defined.
The server needs to know the interface to provide an implementation for it.


## Instructions for using Maven

Make sure that you installed the contract module first.

To compile and run the server:

```
mvn compile exec:java
```
