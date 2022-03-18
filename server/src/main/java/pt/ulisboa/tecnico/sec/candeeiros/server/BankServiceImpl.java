package pt.ulisboa.tecnico.sec.candeeiros.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pt.ulisboa.tecnico.sec.candeeiros.Bank;
import pt.ulisboa.tecnico.sec.candeeiros.BankServiceGrpc;
import pt.ulisboa.tecnico.sec.candeeiros.server.model.BankAccount;
import pt.ulisboa.tecnico.sec.candeeiros.server.model.Transaction;
import pt.ulisboa.tecnico.sec.candeeiros.shared.Crypto;

import io.grpc.stub.StreamObserver;
import pt.ulisboa.tecnico.sec.candeeiros.server.model.BftBank;

import java.math.BigDecimal;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;

public class BankServiceImpl extends BankServiceGrpc.BankServiceImplBase {
	private static final Logger logger = LoggerFactory.getLogger(BankServiceImpl.class);
	private final BftBank bank;

	public BankServiceImpl() {
		super();
		bank = new BftBank();
	}

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
		Bank.OpenAccountResponse.Status status = openAccountStatus(request);

		logger.info("Got request to open account. Status: {}", status);

		switch (status) {
			case SUCCESS:
				PublicKey publicKey = null;
				try {
					publicKey = Crypto.decodePublicKey(request.getPublicKey());
				} catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
					// Should never happen
					e.printStackTrace();
				}
				bank.createAccount(publicKey);
				logger.info("Opened account with public key {}.", Crypto.keyAsShortString(publicKey));
				break;
		}

		Bank.OpenAccountResponse response = Bank.OpenAccountResponse.newBuilder()
				.setStatus(status)
				.build();
		responseObserver.onNext(response);
		responseObserver.onCompleted();
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
			if (bank.getAccount(sourceKey).getBalance().compareTo(amount) < 0)
				return Bank.SendAmountResponse.Status.NOT_ENOUGH_BALANCE;
			return Bank.SendAmountResponse.Status.SUCCESS;
		} catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
			return Bank.SendAmountResponse.Status.INVALID_KEY_FORMAT;
		} catch (NumberFormatException e) {
			return Bank.SendAmountResponse.Status.INVALID_NUMBER_FORMAT;
		}
	}

	@Override
	public void sendAmount(Bank.SendAmountRequest request, StreamObserver<Bank.SendAmountResponse> responseObserver) {
		Bank.SendAmountResponse.Status status = sendAmountStatus(request);

		logger.info("Got request to create transaction. Status: {}", status);

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

				bank.addTransaction(sourceKey, destinationKey, amount);

				logger.info("Created transaction: {} -> {} (amount: {})",
						Crypto.keyAsShortString(sourceKey),
						Crypto.keyAsShortString(destinationKey),
						amount);
				break;
		}

		Bank.SendAmountResponse response = Bank.SendAmountResponse.newBuilder()
				.setStatus(status)
				.build();
		responseObserver.onNext(response);
		responseObserver.onCompleted();
	}

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

	private Bank.ReceiveAmountResponse.Status receiveAmountStatus(Bank.ReceiveAmountRequest request) {
		try {
			PublicKey destinationKey = Crypto.decodePublicKey(request.getTransaction().getDestinationPublicKey());
			PublicKey sourceKey = Crypto.decodePublicKey(request.getTransaction().getSourcePublicKey());

			if (!bank.accountExists(destinationKey))
				return Bank.ReceiveAmountResponse.Status.INVALID_KEY;
			BigDecimal amount = new BigDecimal(request.getTransaction().getAmount());
			if (!bank.getAccount(destinationKey).getTransactionQueue().contains(new Transaction(sourceKey, destinationKey, amount)))
				return Bank.ReceiveAmountResponse.Status.NO_SUCH_TRANSACTION;
			return Bank.ReceiveAmountResponse.Status.SUCCESS;
		} catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
			return Bank.ReceiveAmountResponse.Status.INVALID_KEY_FORMAT;
		} catch (NumberFormatException e) {
			return Bank.ReceiveAmountResponse.Status.NO_SUCH_TRANSACTION;
		}
	}

	@Override
	public void receiveAmount(Bank.ReceiveAmountRequest request, StreamObserver<Bank.ReceiveAmountResponse> responseObserver) {
		Bank.ReceiveAmountResponse.Status status = receiveAmountStatus(request);

		logger.info("Got request to accept transaction. Status: {}", status);

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

				if (request.getAccept()) {
					bank.acceptTransaction(sourceKey, destinationKey, amount);

					logger.info("Applied transaction: {} -> {} (amount: {})",
							Crypto.keyAsShortString(sourceKey),
							Crypto.keyAsShortString(destinationKey),
							amount);
				} else {
					bank.rejectTransaction(sourceKey, destinationKey, amount);

					logger.info("Rejected transaction: {} -> {} (amount: {})",
							Crypto.keyAsShortString(sourceKey),
							Crypto.keyAsShortString(destinationKey),
							amount);
				}
				break;
		}

		Bank.ReceiveAmountResponse response = Bank.ReceiveAmountResponse.newBuilder()
				.setStatus(status)
				.build();
		responseObserver.onNext(response);
		responseObserver.onCompleted();
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
