# gRPC client

This is the gRPC client application.

The client depends on the contract module, where the protocol buffers shared between server and client are defined.
The client needs to know the interface to make remote calls.


## Instructions for using Maven

Make sure that you installed the contract module first.

To compile and run the client:

```
mvn compile exec:java
```
