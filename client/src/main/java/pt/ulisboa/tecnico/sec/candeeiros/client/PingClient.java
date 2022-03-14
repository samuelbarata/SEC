package pt.ulisboa.tecnico.sec.candeeiros.client;

/* these imported classes are generated by the ping contract */
import pt.ulisboa.tecnico.sec.candeeiros.Ping;
import pt.ulisboa.tecnico.sec.candeeiros.PingServiceGrpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PingClient {
	private static final Logger logger = LoggerFactory.getLogger(PingClient.class);

	public static void main(String[] args) {
		logger.info("Ping Client");

		// receive and print arguments
		logger.info("Received {} arguments", args.length);
		for (int i = 0; i < args.length; i++) {
			logger.info("arg[{}] = {}", i, args[i]);
		}

		// check arguments
		if (args.length < 2) {
			logger.error("Argument(s) missing!");
			logger.error("Usage: java {} host port", PingClient.class.getName());
			return;
		}

		final String host = args[0];
		final int port = Integer.parseInt(args[1]);
		final String target = host + ":" + port;

		final String contentToSend = "echo!";

		// Channel is the abstraction to connect to a service endpoint
		// Let us use plaintext communication because we do not have certificates
		final ManagedChannel channel = ManagedChannelBuilder.forTarget(target).usePlaintext().build();

		// It is up to the client to determine whether to block the call
		// Here we create a blocking stub, but an async stub,
		// or an async stub with Future are always possible.
		PingServiceGrpc.PingServiceBlockingStub stub = PingServiceGrpc.newBlockingStub(channel);
		Ping.PingRequest request = Ping.PingRequest.newBuilder().setContent(contentToSend).build();

		// Finally, make the call using the stub
		Ping.PingResponse response = stub.ping(request);

		logger.info("Sent {}", contentToSend);
		logger.info("Received {}", response.getContent());
		if (contentToSend.equals(response.getContent().toString())) {
			logger.info("Strings match!");
		} else {
			logger.error("Strings don't match!");
		}

		// A Channel should be shutdown before stopping the process.
		channel.shutdownNow();
	}

}
