package pt.ulisboa.tecnico.sec.candeeiros.server;

import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import pt.ulisboa.tecnico.sec.candeeiros.shared.Crypto;

import java.security.PrivateKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BankServer {
	private static final Logger logger = LoggerFactory.getLogger(BankServer.class);

	public static void main(String[] args) throws Exception {
		logger.info("Ping Server");

		// Print received arguments.
		logger.info("Received {} arguments", args.length);
		for (int i = 0; i < args.length; i++) {
			logger.info("arg[{}] = {}", i, args[i]);
		}

		// Check arguments.
		if (args.length < 3) {
			logger.error("Argument(s) missing!");
			logger.error("Usage: java {} port ledger_file key_file%n", Server.class.getName());
			return;
		}

		int port = Integer.parseInt(args[0]);
		String ledgeFileName = args[1];
		PrivateKey privateKey = (PrivateKey) Crypto.readKeyOrExit(args[2], "private");;
		final BindableService impl = new BankServiceImpl(ledgeFileName, privateKey);


		// Create a new server to listen on port.
		Server server = ServerBuilder.forPort(port).addService(impl).build();
		// Start the server.
		server.start();
		// Server threads are running in the background.
		logger.info("Server started");

		// Do not exit the main thread. Wait until server is terminated.
		server.awaitTermination();
	}

}
