# CandeeirosBank

This is a gRPC application, composed of four modules:
- [contract](contract/) - protocol buffers definition
- [shared](shared/) - library for shared code
- [server](server/) - implementation of service
- [client](client/) - invocation of service

See the README for each module.  

----

# Development

The intended IDE is IntelliJ Idea. Run configurations are included:

- Compile Contract - Compiles the ProtoBuff definitions
- Compile Shared - Compiles the shared libraries
- Run Server - Runs the server
- Run Client - Runs the client

After modifying the contract, make sure to recompile it (run the Contract run configuration). 

If IntelliJ fails to resolve classes or has otherwise outdated data on the client and server dependencies, run a maven reload by selecting the maven tab on the maven tab on the far right and using the reload button. 
