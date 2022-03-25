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
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;

public class BankServiceImpl extends BankServiceGrpc.BankServiceImplBase {
	private static final Logger logger = LoggerFactory.getLogger(BankServiceImpl.class);
	private final BftBank bank;

	public BankServiceImpl(String ledgerFileName) throws IOException {
		super();
		bank = new BftBank(ledgerFileName);
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
					PublicKey publicKey = null;
					try {
						publicKey = Crypto.decodePublicKey(request.getPublicKey());
					} catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
						// Should never happen
						e.printStackTrace();
					}
					response.setChallenge(ByteString.copyFrom(Crypto.challenge(request.getChallenge().toByteArray())));

					bank.createAccount(publicKey);
					logger.info("Opened account with public key {}.", Crypto.keyAsShortString(publicKey));
					break;
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

					response.setChallenge(ByteString.copyFrom(Crypto.challenge(request.getChallenge().toByteArray())))
							.setNonce(Bank.Nonce.newBuilder().setNonceBytes(ByteString.copyFrom(account.getNonce().getBytes())).build());

					break;
			}

			responseObserver.onNext(response.build());
			responseObserver.onCompleted();
		}
	}



	private Bank.SendAmountResponse.Status sendAmountStatus(Bank.SendAmountRequest request) {
		try {
			PublicKey destinationKey = Crypto.decodePublicKey(request.getTransaction().getDestinationPublicKey());
			PublicKey sourceKey = Crypto.decodePublicKey(request.getTransaction().getSourcePublicKey());

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
		synchronized (bank) {
			Bank.SendAmountResponse.Status status = sendAmountStatus(request);

			logger.info("Got request to create transaction. Status: {}", status);

			Bank.SendAmountResponse.Builder response = Bank.SendAmountResponse.newBuilder()
					.setStatus(status);

			switch (status) {
				case SUCCESS:
					PublicKey sourceKey = null;
					PublicKey destinationKey = null;
					try {
						sourceKey = Crypto.decodePublicKey(request.getTransaction().getSourcePublicKey());
						destinationKey = Crypto.decodePublicKey(request.getTransaction().getDestinationPublicKey());
					} catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
						// Should never happen
						e.printStackTrace();
					}
					BigDecimal amount = new BigDecimal(request.getTransaction().getAmount()); // should never fail
					Nonce nonce = Nonce.decode(request.getNonce());

					bank.addTransaction(sourceKey, destinationKey, amount, nonce);
					response.setNonce(nonce.encode());

					logger.info("Created transaction: {} -> {} (amount: {})",
							Crypto.keyAsShortString(sourceKey),
							Crypto.keyAsShortString(destinationKey),
							amount);
					break;
			}

			responseObserver.onNext(response.build());
			responseObserver.onCompleted();
		}
	}



	private Bank.ReceiveAmountResponse.Status receiveAmountStatus(Bank.ReceiveAmountRequest request) {
		try {
			PublicKey destinationKey = Crypto.decodePublicKey(request.getTransaction().getDestinationPublicKey());
			PublicKey sourceKey = Crypto.decodePublicKey(request.getTransaction().getSourcePublicKey());

			if (!bank.accountExists(destinationKey))
				return Bank.ReceiveAmountResponse.Status.INVALID_KEY;
			BankAccount destinationAccount = bank.getAccount(destinationKey);
			BigDecimal amount = new BigDecimal(request.getTransaction().getAmount());
			if (!destinationAccount.getTransactionQueue().containsKey(new Transaction(sourceKey, destinationKey, amount)))
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
		synchronized (bank) {
			Bank.ReceiveAmountResponse.Status status = receiveAmountStatus(request);

			logger.info("Got request to accept transaction. Status: {}", status);

			Bank.ReceiveAmountResponse.Builder response = Bank.ReceiveAmountResponse.newBuilder()
					.setStatus(status);

			switch (status) {
				case SUCCESS:
					PublicKey sourceKey = null;
					PublicKey destinationKey = null;
					try {
						sourceKey = Crypto.decodePublicKey(request.getTransaction().getSourcePublicKey());
						destinationKey = Crypto.decodePublicKey(request.getTransaction().getDestinationPublicKey());
					} catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
						// Should never happen
						e.printStackTrace();
					}
					BigDecimal amount = new BigDecimal(request.getTransaction().getAmount()); // should never fail
					Nonce nonce = Nonce.decode(request.getNonce());

					response.setNonce(nonce.encode());

					if (request.getAccept()) {
						bank.acceptTransaction(sourceKey, destinationKey, amount, nonce);

						logger.info("Applied transaction: {} -> {} (amount: {})",
								Crypto.keyAsShortString(sourceKey),
								Crypto.keyAsShortString(destinationKey),
								amount);
					} else {
						bank.rejectTransaction(sourceKey, destinationKey, amount, nonce);

						logger.info("Rejected transaction: {} -> {} (amount: {})",
								Crypto.keyAsShortString(sourceKey),
								Crypto.keyAsShortString(destinationKey),
								amount);
					}
					break;
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
							.setChallenge(ByteString.copyFrom(Crypto.challenge(request.getChallenge().toByteArray())));

					for (Transaction t : account.getTransactionQueue().keySet().toArray(new Transaction[0])) {
						Bank.Transaction transaction = Bank.Transaction.newBuilder()
								.setAmount(t.getAmount().toString())
								.setDestinationPublicKey(Crypto.encodePublicKey(t.getDestination()))
								.setSourcePublicKey(Crypto.encodePublicKey(t.getSource()))
								.build();
						response.addTransactions(transaction);
					}
					break;
			}

			responseObserver.onNext(response.build());
			responseObserver.onCompleted();
		}
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

					response.setChallenge(ByteString.copyFrom(Crypto.challenge(request.getChallenge().toByteArray())));

					for (Transaction t : account.getTransactionHistory().keySet().toArray(new Transaction[0])) {
						Bank.Transaction transaction = Bank.Transaction.newBuilder()
								.setAmount(t.getAmount().toString())
								.setDestinationPublicKey(Crypto.encodePublicKey(t.getDestination()))
								.setSourcePublicKey(Crypto.encodePublicKey(t.getSource()))
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
