package pt.ulisboa.tecnico.sec.candeeiros.server;

import com.google.protobuf.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pt.ulisboa.tecnico.sec.candeeiros.Bank;
import pt.ulisboa.tecnico.sec.candeeiros.BankServiceGrpc;
import pt.ulisboa.tecnico.sec.candeeiros.server.model.BankAccount;
import pt.ulisboa.tecnico.sec.candeeiros.server.model.Transaction;
import pt.ulisboa.tecnico.sec.candeeiros.shared.Crypto;

import io.grpc.stub.StreamObserver;
import pt.ulisboa.tecnico.sec.candeeiros.server.model.BftBank;
import pt.ulisboa.tecnico.sec.candeeiros.shared.Nonce;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.List;

public class BankServiceImpl extends BankServiceGrpc.BankServiceImplBase {
	private static final Logger logger = LoggerFactory.getLogger(BankServiceImpl.class);
	private final BftBank bank;
	private final PrivateKey privateKey;

	public BankServiceImpl(String ledgerFileName, PrivateKey privateKey) throws IOException {
		super();
		bank = new BftBank(ledgerFileName);
		this.privateKey = privateKey;
	}

	// ***** Authenticated procedures *****
	private Bank.OpenAccountResponse.Status openAccountStatus(Bank.OpenAccountRequest request) {
		try {
			PublicKey publicKey = Crypto.decodePublicKey(request.getPublicKey());
			if (bank.accountExists(publicKey))
				return Bank.OpenAccountResponse.Status.ALREADY_EXISTED;
			return Bank.OpenAccountResponse.Status.SUCCESS;
		} catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
			return Bank.OpenAccountResponse.Status.KEY_FAILURE;
		}
	}

	@Override
	public void openAccount(Bank.OpenAccountRequest request, StreamObserver<Bank.OpenAccountResponse> responseObserver) {
		synchronized (bank) {
			Bank.OpenAccountResponse.Status status = openAccountStatus(request);

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
			}
			response.setChallengeNonce(request.getChallengeNonce());
			try {
				response.setSignature(Bank.Signature.newBuilder().setSignatureBytes(ByteString.copyFrom(Crypto.sign(privateKey, 
				request.getChallengeNonce().getNonceBytes().toByteArray(),
				status.name().getBytes()
				))).build());
			} catch (InvalidKeyException | SignatureException e) {
				// Should never happen
				e.printStackTrace();
			}
			responseObserver.onNext(response.build());
			responseObserver.onCompleted();
		}
	}



	private Bank.NonceNegotiationResponse.Status nonceNegotiationStatus(Bank.NonceNegotiationRequest request) {
		try {
			PublicKey key = Crypto.decodePublicKey(request.getPublicKey());
			if (!bank.accountExists(key))
				return Bank.NonceNegotiationResponse.Status.INVALID_KEY;
			return Bank.NonceNegotiationResponse.Status.SUCCESS;
		} catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
			return Bank.NonceNegotiationResponse.Status.INVALID_KEY_FORMAT;
		}
	}

	@Override
	public void nonceNegotiation(Bank.NonceNegotiationRequest request, StreamObserver<Bank.NonceNegotiationResponse> responseObserver) {
		synchronized (bank) {
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
			}

			response.setChallengeNonce(request.getChallengeNonce());
			try {
				response.setSignature(Bank.Signature.newBuilder().setSignatureBytes(ByteString.copyFrom(Crypto.sign(privateKey, 
					request.getChallengeNonce().getNonceBytes().toByteArray(),
					status.name().getBytes(),
					response.getNonce().getNonceBytes().toByteArray()
				))).build());
			} catch (InvalidKeyException | SignatureException e) {
				// Should never happen
				e.printStackTrace();
			}

			responseObserver.onNext(response.build());
			responseObserver.onCompleted();
		}
	}



	private Bank.SendAmountResponse.Status sendAmountStatus(Bank.SendAmountRequest request) {
		try {
			PublicKey destinationKey = Crypto.decodePublicKey(request.getTransaction().getDestinationPublicKey());
			PublicKey sourceKey = Crypto.decodePublicKey(request.getTransaction().getSourcePublicKey());

			if(!Crypto.verifySignature(sourceKey, request.getSignature().getSignatureBytes().toByteArray(),
					request.getTransaction().getSourcePublicKey().toByteArray(),
					request.getTransaction().getDestinationPublicKey().toByteArray(),
					request.getTransaction().getAmount().getBytes(),
					request.getNonce().getNonceBytes().toByteArray()
					))
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
		} catch (InvalidKeySpecException | NoSuchAlgorithmException | InvalidKeyException e) {
			return Bank.SendAmountResponse.Status.INVALID_KEY_FORMAT;
		} catch (NumberFormatException e) {
			return Bank.SendAmountResponse.Status.INVALID_NUMBER_FORMAT;
		} catch (SignatureException e) {
			return Bank.SendAmountResponse.Status.INVALID_SIGNATURE;
		}
	}

	@Override
	public void sendAmount(Bank.SendAmountRequest request, StreamObserver<Bank.SendAmountResponse> responseObserver) {
		synchronized (bank) {
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
			}
			response.setNonce(request.getNonce());
			try {
				response.setSignature(Bank.Signature.newBuilder().setSignatureBytes(ByteString.copyFrom(Crypto.sign(privateKey, 
					response.getNonce().getNonceBytes().toByteArray(),
					status.name().getBytes()
				))).build());
			} catch (InvalidKeyException | SignatureException e) {
				// Should never happen
				e.printStackTrace();
			}
			responseObserver.onNext(response.build());
			responseObserver.onCompleted();
		}
	}



	private Bank.ReceiveAmountResponse.Status receiveAmountStatus(Bank.ReceiveAmountRequest request) {
		try {
			PublicKey destinationKey = Crypto.decodePublicKey(request.getTransaction().getDestinationPublicKey());
			PublicKey sourceKey = Crypto.decodePublicKey(request.getTransaction().getSourcePublicKey());
			if(!Crypto.verifySignature(sourceKey, request.getSignature().getSignatureBytes().toByteArray(),
					request.getTransaction().getSourcePublicKey().getKeyBytes().toByteArray(),
					request.getTransaction().getDestinationPublicKey().getKeyBytes().toByteArray(),
					request.getTransaction().getAmount().getBytes(),
					request.getNonce().getNonceBytes().toByteArray()
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
		} catch (InvalidKeySpecException | NoSuchAlgorithmException | InvalidKeyException e) {
			return Bank.ReceiveAmountResponse.Status.INVALID_KEY_FORMAT;
		} catch (NumberFormatException e) {
			return Bank.ReceiveAmountResponse.Status.NO_SUCH_TRANSACTION;
		} catch (SignatureException e) {
			return Bank.ReceiveAmountResponse.Status.INVALID_SIGNATURE;
		}
	}

	@Override
	public void receiveAmount(Bank.ReceiveAmountRequest request, StreamObserver<Bank.ReceiveAmountResponse> responseObserver) {
		synchronized (bank) {
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
			}

			response.setNonce(request.getNonce());

			try {
				response.setSignature(Bank.Signature.newBuilder().setSignatureBytes(ByteString.copyFrom(Crypto.sign(privateKey, 
					request.getNonce().getNonceBytes().toByteArray(),
					status.name().getBytes()
				))).build());
			} catch (InvalidKeyException | SignatureException e) {
				// Should never happen
				e.printStackTrace();
			}

			responseObserver.onNext(response.build());
			responseObserver.onCompleted();
		}
	}



	// ***** Unauthenticated procedures *****
	private Bank.CheckAccountResponse.Status checkAccountStatus(Bank.CheckAccountRequest request) {
		try {
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
		synchronized (bank) {
			Bank.CheckAccountResponse.Status status = checkAccountStatus(request);
			logger.info("Got check account. Status: {}", status);

			Bank.CheckAccountResponse.Builder response = Bank.CheckAccountResponse
					.newBuilder()
					.setStatus(status);

			List<Byte> toSign = new ArrayList<>();
			insertPrimitiveToByteList(toSign, response.getChallengeNonce().getNonceBytes().toByteArray());
			insertPrimitiveToByteList(toSign, status.name().getBytes());

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
					response.setBalance(account.getBalance().toString())
							.setChallengeNonce(request.getChallengeNonce());

					insertPrimitiveToByteList(toSign, account.getBalance().toString().getBytes());

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
						insertPrimitiveToByteList(toSign, t.getSource().getEncoded());
						insertPrimitiveToByteList(toSign, t.getDestination().getEncoded());
						insertPrimitiveToByteList(toSign, t.getAmount().toString().getBytes());
						insertPrimitiveToByteList(toSign, t.getSourceNonce().getBytes());
						insertPrimitiveToByteList(toSign, t.getSourceSignature());
					}
					break;
			}
			
			try {
				response.setSignature(Bank.Signature.newBuilder().setSignatureBytes(ByteString.copyFrom(Crypto.sign(privateKey,
						byteListToPrimitiveByteArray(toSign)
				))).build());
			} catch (InvalidKeyException | SignatureException e) {
				// Should never happen
				e.printStackTrace();
			}
			
			responseObserver.onNext(response.build());
			responseObserver.onCompleted();
		}
	}

	// Java does not support inserting a byte[] into a List<Byte> with addAll due to boxing
	public static void insertPrimitiveToByteList(List<Byte> list, byte[] array) {
		for (byte b : array)
			list.add(b);
	}

	public static byte[] byteListToPrimitiveByteArray(List<Byte> list) {
		byte[] bytes = new byte[list.size()];
		for (int i = 0; i < list.size(); i++) {
			bytes[i] = list.get(i);
		}
		return bytes;
	}

	private Bank.AuditResponse.Status auditStatus(Bank.AuditRequest request) {
		try {
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
		synchronized (bank) {
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

					response.setChallengeNonce(request.getChallengeNonce());

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
								.build();
						response.addTransactions(transaction);
					}
					break;
			}

			responseObserver.onNext(response.build());
			responseObserver.onCompleted();
		}
	}


}
