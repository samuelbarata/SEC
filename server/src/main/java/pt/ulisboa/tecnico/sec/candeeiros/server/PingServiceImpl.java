package pt.ulisboa.tecnico.sec.candeeiros.server;

/* these imported classes are generated by the ping contract */
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pt.ulisboa.tecnico.sec.candeeiros.Ping;
import pt.ulisboa.tecnico.sec.candeeiros.PingServiceGrpc;

import io.grpc.stub.StreamObserver;

public class PingServiceImpl extends PingServiceGrpc.PingServiceImplBase {
	private static final Logger logger = LoggerFactory.getLogger(PingServiceImpl.class);

	@Override
	public void ping(Ping.PingRequest request, StreamObserver<Ping.PingResponse> responseObserver) {

		// PingRequest has auto-generated toString method that shows its contents
		logger.info("Got request with content: {}", request.getContent());

		// You must use a builder to construct a new Protobuffer object
		Ping.PingResponse response = Ping.PingResponse.newBuilder()
				.setContent(request.getContent()).build();

		// Use responseObserver to send a single response back
		responseObserver.onNext(response);

		// When you are done, you must call onCompleted
		responseObserver.onCompleted();
	}

}
