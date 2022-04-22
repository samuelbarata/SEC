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
	private final BftBank bank;
	private final KeyManager keyManager;
	private final List<String> SyncBanksTargets;

	private final ArrayList<ManagedChannel> SyncBanksManagedChannels;
	private final ArrayList<SyncBanksServiceGrpc.SyncBanksServiceBlockingStub> SyncBanksStubs;

	private final ConcurrentHashMap<Integer, Bank.OpenAccountResponse> OpenAccountResponses;
	private final ConcurrentHashMap<Integer, Bank.SendAmountResponse> SendAmountResponses;
	private final ConcurrentHashMap<Integer, Bank.ReceiveAmountResponse> ReceiveAmountResponses;

	private int timestamp;

	public BankServiceImpl(String ledgerFileName, KeyManager keyManager, String SyncBankTarget) throws IOException {
		super();
		bank = new BftBank(ledgerFileName);
		this.keyManager = keyManager;
		this.SyncBanksManagedChannels = new ArrayList<>();
		this.SyncBanksTargets = new ArrayList<>();
		this.SyncBanksStubs = new ArrayList<>();
		this.SyncBanksTargets.add(SyncBankTarget);

		this.OpenAccountResponses = new ConcurrentHashMap<>();
		this.SendAmountResponses = new ConcurrentHashMap<>();
		this.ReceiveAmountResponses = new ConcurrentHashMap<>();
		CreateStubs();

		this.timestamp = 0;
	}

	public void CreateStubs() {
		ManagedChannel channel;
		for(String target: SyncBanksTargets)
		{
			channel = ManagedChannelBuilder.forTarget(target).usePlaintext().build();
			SyncBanksManagedChannels.add(channel);
			SyncBanksStubs.add(SyncBanksServiceGrpc.newBlockingStub(channel));
		}
	}

	// ***** Authenticated procedures *****

	@Override
	public void openAccount(Bank.OpenAccountRequest request, StreamObserver<Bank.OpenAccountResponse> responseObserver) {
		int currentTS = timestamp;
		SyncBanks.OpenAccountIntentRequest.Builder intentRequest = SyncBanks.OpenAccountIntentRequest.newBuilder();
		intentRequest.setOpenAccountRequest(request);
		intentRequest.setTimestamp(timestamp);
		// send intents to all servers
		for(SyncBanksServiceGrpc.SyncBanksServiceBlockingStub stub: SyncBanksStubs)
		{
			logger.info("Bank Service: Sent Open Account Sync");
			stub.openAccountSync(intentRequest.build());
		}
		timestamp++;
		logger.info("Bank Service: Waiting for Open Account Response");
		while (OpenAccountResponses.get(currentTS)==null) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		Bank.OpenAccountResponse responseSync = OpenAccountResponses.get(currentTS);
		Bank.OpenAccountResponse.Builder response = Bank.OpenAccountResponse.newBuilder().setStatus(responseSync.getStatus());
		response.setChallengeNonce(request.getChallengeNonce());
		try{
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
		System.out.println("Open Account Finished");

			/*Bank.OpenAccountResponse.Status status = openAccountStatus(request);

			logger.info("Got request to open account. Status: {}", status);

				Bank.OpenAccountResponse.Builder response = Bank.OpenAccountResponse.newBuilder()
					.setStatus(status);

			switch (status) {
				case SUCCESS:
					PublicKey publicKey;
					try {
						publicKey = Crypto.decodePublicKey(request.getPublicKey());
					} catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
						// Should never happen
						e.printStackTrace();
						break;
					}

					bank.createAccount(publicKey);
					logger.info("Opened account with public key {}.", Crypto.keyAsShortString(publicKey));
					break;
				case INVALID_MESSAGE_FORMAT:
					responseObserver.onNext(response.build());
					responseObserver.onCompleted();
					return;
			}
			response.setChallengeNonce(request.getChallengeNonce());
			try {
				response.setSignature(Bank.Signature.newBuilder()
						.setSignatureBytes(ByteString.copyFrom(Signatures.signOpenAccountResponse(keyManager.getKey(),
								request.getChallengeNonce().getNonceBytes().toByteArray(),
								status.name())))
						.build());
			} catch (InvalidKeyException | SignatureException e) {
				// Should never happen
				e.printStackTrace();
			}
			responseObserver.onNext(response.build());
			responseObserver.onCompleted();*/
	}

	@Override
	public void openAccountSyncRequest(Bank.OpenAccountSync request, StreamObserver<Bank.Ack> responseObserver) {
		System.out.println("Got Sync Request: " + request.getTimestamp());
		Bank.OpenAccountResponse response = request.getOpenAccountResponse();
		OpenAccountResponses.put(request.getTimestamp(), response);
	}

	private Bank.NonceNegotiationResponse.Status nonceNegotiationStatus(Bank.NonceNegotiationRequest request) {
		try {
			if (!request.hasChallengeNonce() || !request.hasSignature() || !request.hasPublicKey())
				return Bank.NonceNegotiationResponse.Status.INVALID_MESSAGE_FORMAT;
			PublicKey key = Crypto.decodePublicKey(request.getPublicKey());
			if (!Signatures.verifyNonceNegotiationRequestSignature(request.getSignature().getSignatureBytes().toByteArray(), key,
					request.getChallengeNonce().getNonceBytes().toByteArray(),
					request.getPublicKey().getKeyBytes().toByteArray()))
				return Bank.NonceNegotiationResponse.Status.INVALID_SIGNATURE;
			if (!bank.accountExists(key))
				return Bank.NonceNegotiationResponse.Status.INVALID_KEY;
			return Bank.NonceNegotiationResponse.Status.SUCCESS;
		} catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
			return Bank.NonceNegotiationResponse.Status.INVALID_KEY_FORMAT;
		}
	}

	@Override
	public void nonceNegotiation(Bank.NonceNegotiationRequest request, StreamObserver<Bank.NonceNegotiationResponse> responseObserver) {
		for(SyncBanksServiceGrpc.SyncBanksServiceBlockingStub stub: SyncBanksStubs)
		{
			responseObserver.onNext(stub.nonceNegotiation(request));
			responseObserver.onCompleted();
		}
		/*synchronized (bank) {
			Bank.NonceNegotiationResponse.Status status = nonceNegotiationStatus(request);
			Bank.NonceNegotiationResponse.Builder response = Bank.NonceNegotiationResponse.newBuilder().setStatus(status);

			logger.info("Got nonce negotiation request. Status: {}", status.name());
			byte[] nonceBytes = new byte[0];

			switch (status) {
				case SUCCESS:
					PublicKey key = null;
					try {
						key = Crypto.decodePublicKey(request.getPublicKey());
					} catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
						// This should not happen
						e.printStackTrace();
					}

					BankAccount account = bank.getAccount(key);
					response.setNonce(account.getNonce().encode());
					break;
				case INVALID_MESSAGE_FORMAT:
					responseObserver.onNext(response.build());
					responseObserver.onCompleted();
					return;
			}

			response.setChallengeNonce(request.getChallengeNonce());
			try {
				response.setSignature(Bank.Signature.newBuilder()
						.setSignatureBytes(ByteString.copyFrom(Signatures.signNonceNegotiationResponse(keyManager.getKey(),
								request.getChallengeNonce().getNonceBytes().toByteArray(),
								status.name()
								)))
						.build());
			} catch (SignatureException | InvalidKeyException e) {
				// should never happen
				e.printStackTrace();
			}

			responseObserver.onNext(response.build());
			responseObserver.onCompleted();
		}*/
	}



	private Bank.SendAmountResponse.Status sendAmountStatus(Bank.SendAmountRequest request) {
		try {
			if (!request.hasSignature() || !request.hasNonce() || !request.hasTransaction() ||
				!request.getTransaction().hasSourcePublicKey() || !request.getTransaction().hasDestinationPublicKey())
				return Bank.SendAmountResponse.Status.INVALID_MESSAGE_FORMAT;

			PublicKey destinationKey = Crypto.decodePublicKey(request.getTransaction().getDestinationPublicKey());
			PublicKey sourceKey = Crypto.decodePublicKey(request.getTransaction().getSourcePublicKey());

			if (!Signatures.verifySendAmountRequestSignature(request.getSignature().getSignatureBytes().toByteArray(), sourceKey,
					request.getTransaction().getSourcePublicKey().getKeyBytes().toByteArray(),
					request.getTransaction().getDestinationPublicKey().getKeyBytes().toByteArray(),
					request.getTransaction().getAmount(),
					request.getNonce().getNonceBytes().toByteArray()))
				return Bank.SendAmountResponse.Status.INVALID_SIGNATURE;
			if (!bank.accountExists(destinationKey))
				return Bank.SendAmountResponse.Status.DESTINATION_INVALID;
			if (!bank.accountExists(sourceKey))
				return Bank.SendAmountResponse.Status.SOURCE_INVALID;
			if (sourceKey.equals(destinationKey))
				return Bank.SendAmountResponse.Status.DESTINATION_INVALID;
			BigDecimal amount = new BigDecimal(request.getTransaction().getAmount());
			if (amount.compareTo(BigDecimal.ZERO) <= 0)
				return Bank.SendAmountResponse.Status.INVALID_NUMBER_FORMAT;
			BankAccount sourceAccount = bank.getAccount(sourceKey);
			if (sourceAccount.getBalance().compareTo(amount) < 0)
				return Bank.SendAmountResponse.Status.NOT_ENOUGH_BALANCE;
			if (!sourceAccount.getNonce().nextNonce().equals(Nonce.decode(request.getNonce())))
				return Bank.SendAmountResponse.Status.INVALID_NONCE;
			return Bank.SendAmountResponse.Status.SUCCESS;
		} catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
			return Bank.SendAmountResponse.Status.INVALID_KEY_FORMAT;
		} catch (NumberFormatException e) {
			return Bank.SendAmountResponse.Status.INVALID_NUMBER_FORMAT;
		}
	}

	@Override
	public void sendAmount(Bank.SendAmountRequest request, StreamObserver<Bank.SendAmountResponse> responseObserver) {
		int currentTS = timestamp;
		SyncBanks.SendAmountIntentRequest.Builder intentRequest = SyncBanks.SendAmountIntentRequest.newBuilder();
		intentRequest.setSendAmountRequest(request);
		intentRequest.setTimestamp(timestamp);
		// send intents to all servers
		for(SyncBanksServiceGrpc.SyncBanksServiceBlockingStub stub: SyncBanksStubs)
		{
			System.out.println("Sent");
			stub.sendAmountSync(intentRequest.build());
			System.out.println("Replied");
		}
		timestamp++;
		System.out.println("Start wait");
		while (SendAmountResponses.get(currentTS)==null) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		Bank.SendAmountResponse responseSync = SendAmountResponses.get(currentTS);
		Bank.SendAmountResponse.Builder response = Bank.SendAmountResponse.newBuilder().setStatus(responseSync.getStatus());
		response.setNonce(request.getNonce());
		try{
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
		System.out.println("Open Account Finished");
		/*synchronized (bank) {
			Bank.SendAmountResponse.Status status = sendAmountStatus(request);

			logger.info("Got request to create transaction. Status: {}", status);

			Bank.SendAmountResponse.Builder response = Bank.SendAmountResponse.newBuilder()
					.setStatus(status);

			switch (status) {
				case SUCCESS:
					PublicKey destinationKey;
					PublicKey sourceKey;
					try {
						destinationKey = Crypto.decodePublicKey(request.getTransaction().getDestinationPublicKey());
						sourceKey = Crypto.decodePublicKey(request.getTransaction().getSourcePublicKey());
					} catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
						// Should never happen
						e.printStackTrace();
						break;
					}
					BigDecimal amount = new BigDecimal(request.getTransaction().getAmount()); // should never fail
					Nonce nonce = Nonce.decode(request.getNonce());
					byte[] signature = request.getSignature().getSignatureBytes().toByteArray();

					bank.addTransaction(sourceKey, destinationKey, amount, nonce, signature);

					logger.info("Created transaction: {} -> {} (amount: {})",
							Crypto.keyAsShortString(sourceKey),
							Crypto.keyAsShortString(destinationKey),
							amount);
					break;
				case INVALID_MESSAGE_FORMAT:
					responseObserver.onNext(response.build());
					responseObserver.onCompleted();
					return;
			}
			response.setNonce(request.getNonce());
			try {
				response.setSignature(Bank.Signature.newBuilder()
						.setSignatureBytes(ByteString.copyFrom(Signatures.signSendAmountResponse(keyManager.getKey(),
								response.getNonce().getNonceBytes().toByteArray(),
								status.name())))
						.build());
			} catch (SignatureException | InvalidKeyException e) {
				// Should never happen
				e.printStackTrace();
			}

			responseObserver.onNext(response.build());
			responseObserver.onCompleted();
		}*/
	}

	@Override
	public void sendAmountSyncRequest(Bank.SendAmountSync request, StreamObserver<Bank.Ack> responseObserver) {
		System.out.println("Got Sync Request: " + request.getTimestamp());
		Bank.SendAmountResponse response = request.getSendAmountResponse();
		SendAmountResponses.put(request.getTimestamp(), response);
	}

	private Bank.ReceiveAmountResponse.Status receiveAmountStatus(Bank.ReceiveAmountRequest request) {
		try {
			if (!request.hasNonce() || !request.hasSignature() || !request.hasTransaction() ||
				!request.getTransaction().hasSourcePublicKey() || !request.getTransaction().hasDestinationPublicKey())
				return Bank.ReceiveAmountResponse.Status.INVALID_MESSAGE_FORMAT;

			PublicKey destinationKey = Crypto.decodePublicKey(request.getTransaction().getDestinationPublicKey());
			PublicKey sourceKey = Crypto.decodePublicKey(request.getTransaction().getSourcePublicKey());

			if (!Signatures.verifyReceiveAmountRequestSignature(request.getSignature().getSignatureBytes().toByteArray(), destinationKey,
					request.getTransaction().getSourcePublicKey().getKeyBytes().toByteArray(),
					request.getTransaction().getDestinationPublicKey().getKeyBytes().toByteArray(),
					request.getTransaction().getAmount(),
					request.getNonce().getNonceBytes().toByteArray(),
					request.getAccept()
					))
				return Bank.ReceiveAmountResponse.Status.INVALID_SIGNATURE;

			if (!bank.accountExists(destinationKey))
				return Bank.ReceiveAmountResponse.Status.INVALID_KEY;
			BankAccount destinationAccount = bank.getAccount(destinationKey);
			BigDecimal amount = new BigDecimal(request.getTransaction().getAmount());
			if (!destinationAccount.getTransactionQueue().contains(new Transaction(sourceKey, destinationKey, amount)))
				return Bank.ReceiveAmountResponse.Status.NO_SUCH_TRANSACTION;
			if (!destinationAccount.getNonce().nextNonce().equals(Nonce.decode(request.getNonce())))
				return Bank.ReceiveAmountResponse.Status.INVALID_NONCE;
			return Bank.ReceiveAmountResponse.Status.SUCCESS;
		} catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
			return Bank.ReceiveAmountResponse.Status.INVALID_KEY_FORMAT;
		} catch (NumberFormatException e) {
			return Bank.ReceiveAmountResponse.Status.NO_SUCH_TRANSACTION;
		}
	}

	@Override
	public void receiveAmount(Bank.ReceiveAmountRequest request, StreamObserver<Bank.ReceiveAmountResponse> responseObserver) {
		int currentTS = timestamp;
		SyncBanks.ReceiveAmountIntentRequest.Builder intentRequest = SyncBanks.ReceiveAmountIntentRequest.newBuilder();
		intentRequest.setReceiveAmountRequest(request);
		intentRequest.setTimestamp(timestamp);
		// send intents to all servers
		for(SyncBanksServiceGrpc.SyncBanksServiceBlockingStub stub: SyncBanksStubs)
		{
			System.out.println("Sent");
			stub.receiveAmountSync(intentRequest.build());
			System.out.println("Replied");
		}
		timestamp++;
		System.out.println("Start wait");
		while (ReceiveAmountResponses.get(currentTS)==null) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		Bank.ReceiveAmountResponse responseSync = ReceiveAmountResponses.get(currentTS);
		Bank.ReceiveAmountResponse.Builder response = Bank.ReceiveAmountResponse.newBuilder().setStatus(responseSync.getStatus());
		response.setNonce(request.getNonce());
		try{
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
		System.out.println("Open Account Finished");
		/*synchronized (bank) {
			Bank.ReceiveAmountResponse.Status status = receiveAmountStatus(request);

			logger.info("Got request to accept transaction. Status: {}", status);

			Bank.ReceiveAmountResponse.Builder response = Bank.ReceiveAmountResponse.newBuilder()
					.setStatus(status);

			switch (status) {
				case SUCCESS:
					PublicKey destinationKey;
					PublicKey sourceKey;
					try {
						sourceKey = Crypto.decodePublicKey(request.getTransaction().getSourcePublicKey());
						destinationKey = Crypto.decodePublicKey(request.getTransaction().getDestinationPublicKey());
					} catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
						// Should never happen
						e.printStackTrace();
						break;
					}
					BigDecimal amount = new BigDecimal(request.getTransaction().getAmount()); // should never fail
					Nonce nonce = Nonce.decode(request.getNonce());
					byte[] signature = request.getSignature().getSignatureBytes().toByteArray();

					if (request.getAccept()) {
						bank.acceptTransaction(sourceKey, destinationKey, amount, nonce, signature);

						logger.info("Applied transaction: {} -> {} (amount: {})",
								Crypto.keyAsShortString(sourceKey),
								Crypto.keyAsShortString(destinationKey),
								amount);
					} else {
						bank.rejectTransaction(sourceKey, destinationKey, amount, nonce, signature);

						logger.info("Rejected transaction: {} -> {} (amount: {})",
								Crypto.keyAsShortString(sourceKey),
								Crypto.keyAsShortString(destinationKey),
								amount);
					}
					break;
				case INVALID_MESSAGE_FORMAT:
					responseObserver.onNext(response.build());
					responseObserver.onCompleted();
					return;
			}

			response.setNonce(request.getNonce());

			try {
				response.setSignature(Bank.Signature.newBuilder()
						.setSignatureBytes(ByteString.copyFrom(Signatures.signReceiveAmountResponse(keyManager.getKey(),
								request.getNonce().getNonceBytes().toByteArray(),
								status.name())))
						.build());
			} catch (SignatureException | InvalidKeyException e) {
				// Should never happen
				e.printStackTrace();
			}

			responseObserver.onNext(response.build());
			responseObserver.onCompleted();
		}*/
	}

	@Override
	public void receiveAmountSyncRequest(Bank.ReceiveAmountSync request, StreamObserver<Bank.Ack> responseObserver) {
		System.out.println("Got Sync Request: " + request.getTimestamp());
		Bank.ReceiveAmountResponse response = request.getReceiveAmountResponse();
		ReceiveAmountResponses.put(request.getTimestamp(), response);
	}

	// ***** Unauthenticated procedures *****
	private Bank.CheckAccountResponse.Status checkAccountStatus(Bank.CheckAccountRequest request) {
		try {
			if (!request.hasChallengeNonce() || !request.hasPublicKey())
				return Bank.CheckAccountResponse.Status.INVALID_MESSAGE_FORMAT;

			PublicKey key = Crypto.decodePublicKey(request.getPublicKey());
			if (!bank.accountExists(key))
				return Bank.CheckAccountResponse.Status.INVALID_KEY;
			return Bank.CheckAccountResponse.Status.SUCCESS;
		} catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
			return Bank.CheckAccountResponse.Status.INVALID_KEY_FORMAT;
		}
	}

	@Override
	public void checkAccount(Bank.CheckAccountRequest request, StreamObserver<Bank.CheckAccountResponse> responseObserver) {

		for(SyncBanksServiceGrpc.SyncBanksServiceBlockingStub stub: SyncBanksStubs)
		{
			responseObserver.onNext(stub.checkAccount(request));
			responseObserver.onCompleted();
		}

		/*synchronized (bank) {
			Bank.CheckAccountResponse.Status status = checkAccountStatus(request);
			logger.info("Got check account. Status: {}", status);

			Bank.CheckAccountResponse.Builder response = Bank.CheckAccountResponse
					.newBuilder()
					.setStatus(status);

			switch (status) {
				case SUCCESS:
					PublicKey key = null;
					try {
						key = Crypto.decodePublicKey(request.getPublicKey());
					} catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
						// This should not happen
						e.printStackTrace();
					}

					BankAccount account = bank.getAccount(key);
					response.setBalance(account.getBalance().toString());

					for (Transaction t : account.getTransactionQueue()) {

						Bank.NonRepudiableTransaction transaction = Bank.NonRepudiableTransaction.newBuilder()
								.setTransaction(
									Bank.Transaction.newBuilder()
									.setAmount(t.getAmount().toString())
									.setDestinationPublicKey(Crypto.encodePublicKey(t.getDestination()))
									.setSourcePublicKey(Crypto.encodePublicKey(t.getSource()))
									.build()
								)
								.setSourceNonce(t.getSourceNonce().encode())
								.setSourceSignature(Bank.Signature.newBuilder()
										.setSignatureBytes(ByteString.copyFrom(t.getSourceSignature()))
										.build()
								)
								.build();
						response.addTransactions(transaction);
					}
					break;
				case INVALID_MESSAGE_FORMAT:
					responseObserver.onNext(response.build());
					responseObserver.onCompleted();
					return;
			}


			try {
				response.setChallengeNonce(request.getChallengeNonce())
						.setSignature(Bank.Signature.newBuilder()
							.setSignatureBytes(ByteString.copyFrom(Signatures.signCheckAccountResponse(keyManager.getKey(),
								request.getChallengeNonce().getNonceBytes().toByteArray(),
								response.getStatus().name(),
								response.getBalance(),
								response.getTransactionsList()
								)))
						.build());
			} catch (SignatureException | InvalidKeyException e) {
				// Should never happen
				e.printStackTrace();
			}

			responseObserver.onNext(response.build());
			responseObserver.onCompleted();
		}*/
	}



	private Bank.AuditResponse.Status auditStatus(Bank.AuditRequest request) {
		try {
			if (!request.hasChallengeNonce() || !request.hasPublicKey())
				return Bank.AuditResponse.Status.INVALID_MESSAGE_FORMAT;

			PublicKey key = Crypto.decodePublicKey(request.getPublicKey());
			if (!bank.accountExists(key))
				return Bank.AuditResponse.Status.INVALID_KEY;
			return Bank.AuditResponse.Status.SUCCESS;
		} catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
			return Bank.AuditResponse.Status.INVALID_KEY_FORMAT;
		}
	}

	@Override
	public void audit(Bank.AuditRequest request, StreamObserver<Bank.AuditResponse> responseObserver) {
		for(SyncBanksServiceGrpc.SyncBanksServiceBlockingStub stub: SyncBanksStubs)
		{
			responseObserver.onNext(stub.audit(request));
			responseObserver.onCompleted();
		}
		/*synchronized (bank) {
			Bank.AuditResponse.Status status = auditStatus(request);

			logger.info("Got request to audit account. Status {}", status.name());

			Bank.AuditResponse.Builder response = Bank.AuditResponse.newBuilder().setStatus(status);

			switch (status) {
				case SUCCESS:
					PublicKey key = null;
					try {
						key = Crypto.decodePublicKey(request.getPublicKey());
					} catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
						// This should not happen
						e.printStackTrace();
					}

					BankAccount account = bank.getAccount(key);


					for (Transaction t : account.getTransactionHistory()) {
						Bank.NonRepudiableTransaction transaction = Bank.NonRepudiableTransaction.newBuilder()
								.setTransaction(
										Bank.Transaction.newBuilder()
										.setAmount(t.getAmount().toString())
										.setDestinationPublicKey(Crypto.encodePublicKey(t.getDestination()))
										.setSourcePublicKey(Crypto.encodePublicKey(t.getSource()))
										.build()
								)
								.setSourceNonce(t.getSourceNonce().encode())
								.setDestinationNonce(t.getDestinationNonce().encode())
								.setSourceSignature(Bank.Signature.newBuilder()
										.setSignatureBytes(ByteString.copyFrom(t.getSourceSignature()))
										.build()
								)
								.setDestinationSignature(Bank.Signature.newBuilder()
										.setSignatureBytes(ByteString.copyFrom(t.getDestinationSignature()))
										.build()
								)
								.build();
						response.addTransactions(transaction);
					}
					break;
				case INVALID_MESSAGE_FORMAT:
					responseObserver.onNext(response.build());
					responseObserver.onCompleted();
					return;
			}

			try {
				response.setChallengeNonce(request.getChallengeNonce())
						.setSignature(Bank.Signature.newBuilder()
								.setSignatureBytes(ByteString.copyFrom(Signatures.signAuditResponse(keyManager.getKey(),
										request.getChallengeNonce().getNonceBytes().toByteArray(),
										response.getStatus().name(),
										response.getTransactionsList()
								)))
								.build());
			} catch (SignatureException | InvalidKeyException e) {
				// should never happen
				e.printStackTrace();
			}

			responseObserver.onNext(response.build());
			responseObserver.onCompleted();
		}*/
	}
}
