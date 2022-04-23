package pt.ulisboa.tecnico.sec.candeeiros.server;

import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import pt.ulisboa.tecnico.sec.candeeiros.shared.Crypto;
import pt.ulisboa.tecnico.sec.candeeiros.shared.KeyManager;

import java.security.PrivateKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BankServer {
	private static final Logger logger = LoggerFactory.getLogger(BankServer.class);
	public static KeyManager keyManager;

	public static void main(String[] args) throws Exception {
		logger.info("Ping Server");

		// Print received arguments.
		logger.info("Received {} arguments", args.length);
		for (int i = 0; i < args.length; i++) {
			logger.info("arg[{}] = {}", i, args[i]);
		}

		// Check arguments.
		if (args.length < 7) {
			logger.error("Argument(s) missing!");
			logger.error("Usage: java {} port ledger_file private_key_file keyStore_file certificate server_name%n",
					Server.class.getName());
			return;
		}

		int port = Integer.parseInt(args[0]);
		String ledgeFileName = args[1];
		String key_path = args[2];
		String keyStore_path = args[3];
		String cert_path = args[4];
		int id = Integer.parseInt(args[5]);
		int totalServers = Integer.parseInt(args[6]);
		String server_name = args[7];

		keyManager = new KeyManager(key_path, keyStore_path, "0".toCharArray(), "0".toCharArray(), server_name + "key", cert_path);

		final LightSwitch lSwitch = new LightSwitch();

		final BindableService impl = (BindableService) new BankServiceImpl(ledgeFileName, keyManager,
				"localhost:" + (port + id), lSwitch);
		final BindableService implSync = (BindableService) new SyncBanksServiceImpl(ledgeFileName, keyManager,
				totalServers, "localhost:" + (port + id), port);

		// Create a new server to listen on port.
		Server server = ServerBuilder.forPort(port+id).addService(impl).addService(implSync).build();
		// Start the server.
		server.start();
		// Server threads are running in the background.
		logger.info("Server started");

		// Do not exit the main thread. Wait until server is terminated.
		server.awaitTermination();
	}

}
