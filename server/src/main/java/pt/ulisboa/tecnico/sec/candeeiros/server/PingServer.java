package pt.ulisboa.tecnico.sec.candeeiros.server;

import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerBuilder;

public class PingServer {

	/** Server host port. */
	private static int port;

	public static void main(String[] args) throws Exception {
		System.out.println(PingServer.class.getSimpleName());

		// Print received arguments.
		System.out.printf("Received %d arguments%n", args.length);
		for (int i = 0; i < args.length; i++) {
			System.out.printf("arg[%d] = %s%n", i, args[i]);
		}

		// Check arguments.
		if (args.length < 1) {
			System.err.println("Argument(s) missing!");
			System.err.printf("Usage: java %s port%n", Server.class.getName());
			return;
		}

		port = Integer.valueOf(args[0]);
		final BindableService impl = new PingServiceImpl();

		// Create a new server to listen on port.
		Server server = ServerBuilder.forPort(port).addService(impl).build();
		// Start the server.
		server.start();
		// Server threads are running in the background.
		System.out.println("Server started");

		// Do not exit the main thread. Wait until server is terminated.
		server.awaitTermination();
	}

}
