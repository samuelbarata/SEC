package pt.ulisboa.tecnico.sec.candeeiros.server;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pt.ulisboa.tecnico.sec.candeeiros.Bank;
import pt.ulisboa.tecnico.sec.candeeiros.BankServiceGrpc;
import pt.ulisboa.tecnico.sec.candeeiros.SyncBanks;
import pt.ulisboa.tecnico.sec.candeeiros.SyncBanksServiceGrpc;
import pt.ulisboa.tecnico.sec.candeeiros.server.model.BankAccount;
import pt.ulisboa.tecnico.sec.candeeiros.server.model.Transaction;
import pt.ulisboa.tecnico.sec.candeeiros.shared.Crypto;
import pt.ulisboa.tecnico.sec.candeeiros.shared.KeyManager;

import io.grpc.stub.StreamObserver;
import pt.ulisboa.tecnico.sec.candeeiros.server.model.BftBank;
import pt.ulisboa.tecnico.sec.candeeiros.shared.Nonce;
import pt.ulisboa.tecnico.sec.candeeiros.shared.Signatures;

import java.io.IOException;
import java.math.BigDecimal;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class BankServiceImpl extends BankServiceGrpc.BankServiceImplBase {
	private static final Logger logger = LoggerFactory.getLogger(BankServiceImpl.class);
	private final KeyManager keyManager;
	private final List<String> SyncBanksTargets;

	private final ArrayList<SyncBanksServiceGrpc.SyncBanksServiceBlockingStub> SyncBanksStubs;

	private final ConcurrentHashMap<Integer, Bank.OpenAccountResponse> OpenAccountResponses;
	private final ConcurrentHashMap<Integer, Bank.SendAmountResponse> SendAmountResponses;
	private final ConcurrentHashMap<Integer, Bank.ReceiveAmountResponse> ReceiveAmountResponses;

	private final LightSwitch lightSwitch;

	public BankServiceImpl(KeyManager keyManager, String SyncBankTarget, LightSwitch lswitch) {
		super();
		this.keyManager = keyManager;
		this.SyncBanksTargets = new ArrayList<>();
		this.SyncBanksStubs = new ArrayList<>();
		this.SyncBanksTargets.add(SyncBankTarget);

		this.OpenAccountResponses = new ConcurrentHashMap<>();
		this.SendAmountResponses = new ConcurrentHashMap<>();
		this.ReceiveAmountResponses = new ConcurrentHashMap<>();

		this.lightSwitch = lswitch;
		CreateStubs();
	}

	public Bank.Ack buildAck() {
		return Bank.Ack.newBuilder().build();
	}

	public void CreateStubs() {
		ManagedChannel channel;
		for (String target : SyncBanksTargets) {
			channel = ManagedChannelBuilder.forTarget(target).usePlaintext().build();
			SyncBanksStubs.add(SyncBanksServiceGrpc.newBlockingStub(channel));
		}
	}

	private boolean blockRequest(PublicKey pubKey, StreamObserver<?> responseObserver) {
		if (!lightSwitch.isLightOn(pubKey)) {
			responseObserver.onError(new RuntimeException("Blocked by DDOS protection"));
			return true;
		}
		return false;
	}

	// ***** Authenticated procedures *****

	@Override
	public void openAccount(Bank.OpenAccountRequest request,
			StreamObserver<Bank.OpenAccountResponse> responseObserver) {
		// DOS protections
		try {
			PublicKey pubKey = Crypto.decodePublicKey(request.getPublicKey());
			if (blockRequest(pubKey, responseObserver))
				return;
		} catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
			e.printStackTrace();
			return;
		}

		int currentTS = 0;
		SyncBanks.OpenAccountIntentRequest.Builder intentRequest = SyncBanks.OpenAccountIntentRequest.newBuilder();
		intentRequest.setOpenAccountRequest(request);
		// send intents to all servers
		for (SyncBanksServiceGrpc.SyncBanksServiceBlockingStub stub : SyncBanksStubs) {
			logger.info("Bank Service: Sent Open Account Sync");
			currentTS = stub.openAccountSync(intentRequest.build()).getTimestamp();
		}
		logger.info("Bank Service: Waiting for Open Account Response");
		while (OpenAccountResponses.get(currentTS) == null) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		Bank.OpenAccountResponse responseSync = OpenAccountResponses.get(currentTS);
		Bank.OpenAccountResponse.Builder response = Bank.OpenAccountResponse.newBuilder()
				.setStatus(responseSync.getStatus());
		response.setChallengeNonce(request.getChallengeNonce());
		try {
			response.setSignature(Bank.Signature.newBuilder()
					.setSignatureBytes(ByteString.copyFrom(Signatures.signOpenAccountResponse(keyManager.getKey(),
							request.getChallengeNonce().getNonceBytes().toByteArray(),
							responseSync.getStatus().name())))
					.build());
		} catch (InvalidKeyException | SignatureException e) {
			// Should never happen
			e.printStackTrace();
		}

		responseObserver.onNext(response.build());
		responseObserver.onCompleted();
		logger.info("Open Account Finished");
	}

	@Override
	public void openAccountSyncRequest(Bank.OpenAccountSync request, StreamObserver<Bank.Ack> responseObserver) {
		logger.info("Got Sync Request: " + request.getTimestamp() + " and status: " + request.getOpenAccountResponse().getStatus());
		Bank.OpenAccountResponse response = request.getOpenAccountResponse();
		OpenAccountResponses.put(request.getTimestamp(), response);
		responseObserver.onNext(buildAck());
		responseObserver.onCompleted();
	}

	@Override
	public void nonceNegotiation(Bank.NonceNegotiationRequest request,
			StreamObserver<Bank.NonceNegotiationResponse> responseObserver) {
		for (SyncBanksServiceGrpc.SyncBanksServiceBlockingStub stub : SyncBanksStubs) {
			responseObserver.onNext(stub.nonceNegotiation(request));
			responseObserver.onCompleted();
		}
	}

	@Override
	public void sendAmount(Bank.SendAmountRequest request, StreamObserver<Bank.SendAmountResponse> responseObserver) {

		// DOS protections
		try {
			PublicKey pubKey = Crypto.decodePublicKey(request.getTransaction().getSourcePublicKey());
			if (blockRequest(pubKey, responseObserver))
				return;
		} catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
			e.printStackTrace();
			return;
		}

		int currentTS = 0;
		SyncBanks.SendAmountIntentRequest.Builder intentRequest = SyncBanks.SendAmountIntentRequest.newBuilder();
		intentRequest.setSendAmountRequest(request);
		// send intents to all servers
		for (SyncBanksServiceGrpc.SyncBanksServiceBlockingStub stub : SyncBanksStubs) {
			currentTS = stub.sendAmountSync(intentRequest.build()).getTimestamp();
		}

		while (SendAmountResponses.get(currentTS) == null) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		Bank.SendAmountResponse responseSync = SendAmountResponses.get(currentTS);
		Bank.SendAmountResponse.Builder response = Bank.SendAmountResponse.newBuilder()
				.setStatus(responseSync.getStatus());
		response.setNonce(request.getNonce());
		try {
			response.setSignature(Bank.Signature.newBuilder()
					.setSignatureBytes(ByteString.copyFrom(Signatures.signSendAmountResponse(keyManager.getKey(),
							request.getNonce().getNonceBytes().toByteArray(),
							responseSync.getStatus().name())))
					.build());
		} catch (InvalidKeyException | SignatureException e) {
			// Should never happen
			e.printStackTrace();
		}

		responseObserver.onNext(response.build());
		responseObserver.onCompleted();
		logger.info("Send Amount Finished");
	}

	@Override
	public void sendAmountSyncRequest(Bank.SendAmountSync request, StreamObserver<Bank.Ack> responseObserver) {
		logger.info("Got Sync Request: " + request.getTimestamp());
		Bank.SendAmountResponse response = request.getSendAmountResponse();
		SendAmountResponses.put(request.getTimestamp(), response);
		responseObserver.onNext(buildAck());
		responseObserver.onCompleted();
	}

	@Override
	public void receiveAmount(Bank.ReceiveAmountRequest request,
			StreamObserver<Bank.ReceiveAmountResponse> responseObserver) {

		// DOS protections
		try {
			PublicKey pubKey = Crypto.decodePublicKey(request.getTransaction().getDestinationPublicKey());
			if (blockRequest(pubKey, responseObserver))
				return;
		} catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
			e.printStackTrace();
			return;
		}

		int currentTS = 0;
		SyncBanks.ReceiveAmountIntentRequest.Builder intentRequest = SyncBanks.ReceiveAmountIntentRequest.newBuilder();
		intentRequest.setReceiveAmountRequest(request);
		// send intents to all servers
		for (SyncBanksServiceGrpc.SyncBanksServiceBlockingStub stub : SyncBanksStubs) {
			currentTS = stub.receiveAmountSync(intentRequest.build()).getTimestamp();
		}

		while (ReceiveAmountResponses.get(currentTS) == null) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		Bank.ReceiveAmountResponse responseSync = ReceiveAmountResponses.get(currentTS);
		Bank.ReceiveAmountResponse.Builder response = Bank.ReceiveAmountResponse.newBuilder()
				.setStatus(responseSync.getStatus());
		response.setNonce(request.getNonce());
		try {
			response.setSignature(Bank.Signature.newBuilder()
					.setSignatureBytes(ByteString.copyFrom(Signatures.signReceiveAmountResponse(keyManager.getKey(),
							request.getNonce().getNonceBytes().toByteArray(),
							responseSync.getStatus().name())))
					.build());
		} catch (InvalidKeyException | SignatureException e) {
			// Should never happen
			e.printStackTrace();
		}

		responseObserver.onNext(response.build());
		responseObserver.onCompleted();
		logger.info("Receive Amount Finished");
	}

	@Override
	public void receiveAmountSyncRequest(Bank.ReceiveAmountSync request, StreamObserver<Bank.Ack> responseObserver) {
		logger.info("Got Sync Request: " + request.getTimestamp());
		Bank.ReceiveAmountResponse response = request.getReceiveAmountResponse();
		ReceiveAmountResponses.put(request.getTimestamp(), response);
		responseObserver.onNext(buildAck());
		responseObserver.onCompleted();

	}

	// ***** Unauthenticated procedures *****

	@Override
	public void checkAccount(Bank.CheckAccountRequest request,
			StreamObserver<Bank.CheckAccountResponse> responseObserver) {
		logger.info("Sending Check Account Request to Sync");
		for (SyncBanksServiceGrpc.SyncBanksServiceBlockingStub stub : SyncBanksStubs) {
			responseObserver.onNext(stub.checkAccount(request));
			responseObserver.onCompleted();
		}
	}

	@Override
	public void audit(Bank.AuditRequest request, StreamObserver<Bank.AuditResponse> responseObserver) {
		logger.info("Sending Audit Request to Sync");
		for (SyncBanksServiceGrpc.SyncBanksServiceBlockingStub stub : SyncBanksStubs) {
			responseObserver.onNext(stub.audit(request));
			responseObserver.onCompleted();
		}
	}
}
