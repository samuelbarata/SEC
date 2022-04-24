package pt.ulisboa.tecnico.sec.candeeiros.server;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pt.ulisboa.tecnico.sec.candeeiros.Bank;
import pt.ulisboa.tecnico.sec.candeeiros.BankServiceGrpc;
import pt.ulisboa.tecnico.sec.candeeiros.SyncBanks;
import pt.ulisboa.tecnico.sec.candeeiros.SyncBanksServiceGrpc;
import pt.ulisboa.tecnico.sec.candeeiros.server.model.*;
import pt.ulisboa.tecnico.sec.candeeiros.shared.Crypto;
import pt.ulisboa.tecnico.sec.candeeiros.shared.KeyManager;
import pt.ulisboa.tecnico.sec.candeeiros.shared.Nonce;
import pt.ulisboa.tecnico.sec.candeeiros.shared.Signatures;

import java.io.IOException;
import java.math.BigDecimal;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class SyncBanksServiceImpl extends SyncBanksServiceGrpc.SyncBanksServiceImplBase {

    private static final Logger logger = LoggerFactory.getLogger(BankServiceImpl.class);

    private int timestamp;
    private final BftBank bank;
    private final KeyManager keyManager;

    //Open Account Storages
    private final ConcurrentHashMap<Integer, ArrayList<openAccountIntent>> openAccountIntents;
    private final ConcurrentHashMap<Integer, Bank.OpenAccountResponse> openAccountResponses;
    private final ConcurrentHashMap<SyncBanks.OpenAccountAppliedRequest, Integer> openAppliedCounter;
    private final ArrayList<SyncBanks.OpenAccountIntentRequest> openAccountOriginalsSync;

    //Send Amount Storages
    private final ConcurrentHashMap<Integer, ArrayList<sendAmountIntent>> sendAmountIntents;
    private final ConcurrentHashMap<Integer, Bank.SendAmountResponse> sendAmountResponses;
    private final ConcurrentHashMap<SyncBanks.SendAmountAppliedRequest, Integer> sendAmountAppliedCounter;
    private final ArrayList<SyncBanks.SendAmountIntentRequest> sendAmountOriginalsSync;

    //Receive Amount Storages
    private final ConcurrentHashMap<Integer, ArrayList<receiveAmountIntent>> receiveAmountIntents;
    private final ConcurrentHashMap<Integer, Bank.ReceiveAmountResponse> receiveAmountResponses;
    private final ConcurrentHashMap<SyncBanks.ReceiveAmountAppliedRequest, Integer> receiveAmountAppliedCounter;
    private final ArrayList<SyncBanks.ReceiveAmountIntentRequest> receiveAmountOriginalsSync;

    //Communication between SyncBanks
    private final List<String> SyncBanksTargets;
    private final ArrayList<SyncBanksServiceGrpc.SyncBanksServiceBlockingStub> SyncBanksStubs;

    //Communication between Banks
    private final String BankTarget;
    private BankServiceGrpc.BankServiceBlockingStub BankStub;

    //***
    private final int totalServers;
    private final int port;

    SyncBanksServiceImpl(String ledgerFileName, KeyManager keyManager, int totalServers, String bankTarget, int port) throws IOException {
        super();
        timestamp = -1;
        this.bank = new BftBank(ledgerFileName);
        this.BankTarget = bankTarget;

        this.openAccountIntents = new ConcurrentHashMap<>();
        this.openAccountResponses = new ConcurrentHashMap<>();
        this.openAccountOriginalsSync = new ArrayList<>();

        this.sendAmountIntents = new ConcurrentHashMap<>();
        this.sendAmountResponses = new ConcurrentHashMap<>();
        this.sendAmountOriginalsSync = new ArrayList<>();

        this.receiveAmountIntents = new ConcurrentHashMap<>();
        this.receiveAmountResponses = new ConcurrentHashMap<>();
        this.receiveAmountOriginalsSync = new ArrayList<>();

        this.SyncBanksTargets = new ArrayList<>();
        this.SyncBanksStubs = new ArrayList<>();

        this.openAppliedCounter = new ConcurrentHashMap<>();
        this.sendAmountAppliedCounter = new ConcurrentHashMap<>();
        this.receiveAmountAppliedCounter = new ConcurrentHashMap<>();

        this.totalServers = totalServers;
        this.port = port;
        this.keyManager = keyManager;
        CreateStubs();
        logger.info("Servers needed for majority: {}, total servers: {}", totalServers %2==0 ? (Math.ceil((double)(totalServers+1)/2)) : (Math.ceil((double)(totalServers)/2)), totalServers);
    }

    public void CreateStubs()
    {
        for(int i=0;i<this.totalServers; i++) {
            this.SyncBanksTargets.add("localhost:" + (port + i));
            logger.info("Added SyncServer localhost:" + (port+i));
        }
        ManagedChannel channel;

        for(String target: SyncBanksTargets) {
            channel = ManagedChannelBuilder.forTarget(target).usePlaintext().build();
            SyncBanksStubs.add(SyncBanksServiceGrpc.newBlockingStub(channel));
        }

        ManagedChannel bankManagedChannel = ManagedChannelBuilder.forTarget(BankTarget).usePlaintext().build();
        BankStub = BankServiceGrpc.newBlockingStub(bankManagedChannel);
        logger.info("Created Stubs");
    }

    public Bank.Ack buildAck() {
        return Bank.Ack.newBuilder().build();
    }

    public Bank.Ack buildAck(int timestamp) {
        return Bank.Ack.newBuilder().setTimestamp(timestamp).build();
    }
    // ***** Authenticated procedures *****

    // ***** Open Account *****

    private Bank.OpenAccountResponse.Status openAccountStatus(Bank.OpenAccountRequest request) {
        try {
            if (!request.hasSignature() || !request.hasChallengeNonce() || !request.hasPublicKey())
                return Bank.OpenAccountResponse.Status.INVALID_MESSAGE_FORMAT;

            if (!Signatures.verifyOpenAccountRequestSignature(
                    request.getSignature().getSignatureBytes().toByteArray(),
                    Crypto.decodePublicKey(request.getPublicKey()),
                    request.getChallengeNonce().getNonceBytes().toByteArray(),
                    request.getPublicKey().getKeyBytes().toByteArray()
            ))
                return Bank.OpenAccountResponse.Status.INVALID_SIGNATURE;

            PublicKey publicKey = Crypto.decodePublicKey(request.getPublicKey());
            if (bank.accountExists(publicKey))
                return Bank.OpenAccountResponse.Status.ALREADY_EXISTED;
            return Bank.OpenAccountResponse.Status.SUCCESS;
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            return Bank.OpenAccountResponse.Status.KEY_FAILURE;
        }
    }

    public void openAccount(Bank.OpenAccountRequest request) {
        synchronized (bank) {
                PublicKey publicKey = null;
                try {
                    publicKey = Crypto.decodePublicKey(request.getPublicKey());
                } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
                    // Should never happen
                    e.printStackTrace();
                }
                bank.createAccount(publicKey);
                logger.info("Opened account with public key {}.", Crypto.keyAsShortString(publicKey));
        }
    }

    public Bank.OpenAccountResponse openAccountResponseBuilder(Bank.OpenAccountResponse.Status status) {
        // Create new Open Account Response
        Bank.OpenAccountResponse.Builder request = Bank.OpenAccountResponse.newBuilder();
        request.setStatus(status);

        return request.build();
    }

    public SyncBanks.OpenAccountIntentRequest openAccountRequestBuilder(SyncBanks.OpenAccountIntentRequest request) {
        //Create new Open Account Intent Request
        SyncBanks.OpenAccountIntentRequest.Builder newRequest = SyncBanks.OpenAccountIntentRequest.newBuilder();
        newRequest.setOpenAccountRequest(request.getOpenAccountRequest());
        newRequest.setTimestamp(this.timestamp + 1);

        //Security (signing, nonces)


        //Return
        openAccountOriginalsSync.add(newRequest.build());
        return newRequest.build();
    }

    @Override
    public void openAccountSync(SyncBanks.OpenAccountIntentRequest request, StreamObserver<Bank.Ack> responseObserver) {

        SyncBanks.OpenAccountIntentRequest newRequest = openAccountRequestBuilder(request);
        responseObserver.onNext(buildAck(newRequest.getTimestamp()));
        responseObserver.onCompleted();
        logger.info("OpenAccountSync: Sent Open Account Intent with TS: " + newRequest.getTimestamp());
        for(SyncBanksServiceGrpc.SyncBanksServiceBlockingStub stub: SyncBanksStubs)
        {
            stub.openAccountIntent(newRequest);
        }
    }

    @Override
    public void openAccountIntent(SyncBanks.OpenAccountIntentRequest request, StreamObserver<Bank.Ack> responseObserver) {
        responseObserver.onNext(buildAck());
        responseObserver.onCompleted();

        // receive intent to open account

        logger.info("Open Account: Got Intent"); //TODO add from which server id it got

        Bank.OpenAccountResponse.Status status = null;

        if(openAccountIntents.containsKey(request.getTimestamp()) || request.getTimestamp() <= this.timestamp) {
            status = Bank.OpenAccountResponse.Status.INVALID_TIMESTAMP;
            logger.info("Invalid TS");
        } else {
            this.timestamp = request.getTimestamp();
        }

        openAccountIntents.computeIfAbsent(request.getTimestamp(), k -> new ArrayList<>());
        openAccountIntents.get(request.getTimestamp()).add(new openAccountIntent(request.getTimestamp(), request.getOpenAccountRequest()));

        // if timestamp is valid check status if account can be opened
        if(status == null) status = openAccountStatus(request.getOpenAccountRequest());

        // send status to all other servers
        SyncBanks.OpenAccountStatusRequest.Builder statusRequest = SyncBanks.OpenAccountStatusRequest.newBuilder();
        statusRequest.setOpenAccountRequest(request.getOpenAccountRequest());
        statusRequest.setOpenAccountResponse(openAccountResponseBuilder(status));
        statusRequest.setTimestamp(request.getTimestamp());

        for(SyncBanksServiceGrpc.SyncBanksServiceBlockingStub stub: SyncBanksStubs)
        {
            stub.openAccountStatus(statusRequest.build());
        }
    }

    @Override
    public synchronized void openAccountStatus(SyncBanks.OpenAccountStatusRequest request, StreamObserver<Bank.Ack> responseObserver) {
        responseObserver.onNext(buildAck());
        responseObserver.onCompleted();
        logger.info("Open Account: Got Status"); //TODO add from which server id it got

        openAccountIntent currentIntent = null;
        while(openAccountIntents.get(request.getTimestamp())==null) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        while(currentIntent == null) {
            for (openAccountIntent intent : openAccountIntents.get(request.getTimestamp())) {
                if (intent.getRequest().equals(request.getOpenAccountRequest())) {
                    currentIntent = intent;
                    break;
                }
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        // add to status array of this open account intent
        currentIntent.addStatus(request.getOpenAccountResponse().getStatus());

        // check if majority was achieved
        if(currentIntent.hasMajority(totalServers)) {
            currentIntent.majorityChecked();
            if(currentIntent.getMajority()==Bank.OpenAccountResponse.Status.INVALID_TIMESTAMP) {
                logger.info("Invalid Timestamp");
                for (SyncBanks.OpenAccountIntentRequest intentRequest : openAccountOriginalsSync) {
                    if (intentRequest.getOpenAccountRequest().equals(request.getOpenAccountRequest())) {
                        Bank.OpenAccountSync.Builder syncResponse = Bank.OpenAccountSync.newBuilder();
                        syncResponse.setOpenAccountResponse(openAccountResponseBuilder(request.getOpenAccountResponse().getStatus()));
                        syncResponse.setTimestamp(currentIntent.getTimestamp());
                        BankStub.openAccountSyncRequest(syncResponse.build());
                    }
                }
                return;
            }
            openAccountResponses.put(request.getTimestamp(), request.getOpenAccountResponse());
            // if so, apply
            if(currentIntent.getMajority()==Bank.OpenAccountResponse.Status.SUCCESS)
                openAccount(currentIntent.getRequest());
            // send apply request to all other servers
            SyncBanks.OpenAccountAppliedRequest.Builder appliedRequest = SyncBanks.OpenAccountAppliedRequest.newBuilder();
            appliedRequest.setOpenAccountRequest(currentIntent.getRequest());
            appliedRequest.setTimestamp(currentIntent.getTimestamp());

            for(SyncBanksServiceGrpc.SyncBanksServiceBlockingStub stub: SyncBanksStubs)
            {
                stub.openAccountApplied(appliedRequest.build());
            }
        }
    }

    @Override
    public void openAccountApplied(SyncBanks.OpenAccountAppliedRequest request, StreamObserver<Bank.Ack> responseObserver) {
        responseObserver.onNext(buildAck());
        responseObserver.onCompleted();
        logger.info("Open Account: Got Applied"); //TODO add from which server id it got
        // add to applied array of this open account applied
        openAppliedCounter.merge(request, 1, Integer::sum);
        // check if majority was achieved
        boolean found = false;
        for (SyncBanks.OpenAccountIntentRequest intentRequest : openAccountOriginalsSync) {
            if (intentRequest.getOpenAccountRequest().equals(request.getOpenAccountRequest())) {
                found = true;
                break;
            }
        }

        if(!found) return;

        if(openAppliedCounter.get(request)>=(Math.ceil((double)totalServers/2))) {
            logger.info("Open Account: Applied Majority");
            // if so, send to client the requests result
            Bank.OpenAccountSync.Builder syncResponse = Bank.OpenAccountSync.newBuilder();
            syncResponse.setOpenAccountResponse(openAccountResponses.get(request.getTimestamp()));
            syncResponse.setTimestamp(request.getTimestamp());
            BankStub.openAccountSyncRequest(syncResponse.build());
        }
    }
    // ***** Send Amount

    public void sendAmount(Bank.SendAmountRequest request) {
        synchronized (bank) {
            PublicKey destinationKey = null;
            PublicKey sourceKey = null;
            try {
                destinationKey = Crypto.decodePublicKey(request.getTransaction().getDestinationPublicKey());
                sourceKey = Crypto.decodePublicKey(request.getTransaction().getSourcePublicKey());
            } catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
                // Should never happen
                e.printStackTrace();
            }
            BigDecimal amount = new BigDecimal(request.getTransaction().getAmount()); // should never fail
            Nonce nonce = Nonce.decode(request.getNonce());
            byte[] signature = request.getSignature().getSignatureBytes().toByteArray();

            bank.addTransaction(sourceKey, destinationKey, amount, nonce, signature);

            logger.info("Created transaction: {} -> {} (amount: {})",
                    Crypto.keyAsShortString(sourceKey),
                    Crypto.keyAsShortString(destinationKey),
                    amount);
        }
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

    public Bank.SendAmountResponse sendAmountResponseBuilder(Bank.SendAmountResponse.Status status) {
        Bank.SendAmountResponse.Builder request = Bank.SendAmountResponse.newBuilder();
        request.setStatus(status);

        return request.build();
    }

    public SyncBanks.SendAmountIntentRequest sendAmountIntentRequestBuilder(SyncBanks.SendAmountIntentRequest request) {
        SyncBanks.SendAmountIntentRequest.Builder newRequest = SyncBanks.SendAmountIntentRequest.newBuilder();
        newRequest.setSendAmountRequest(request.getSendAmountRequest());
        newRequest.setTimestamp(this.timestamp + 1);

        sendAmountOriginalsSync.add(newRequest.build());
        return newRequest.build();
    }

    @Override
    public void sendAmountSync(SyncBanks.SendAmountIntentRequest request, StreamObserver<Bank.Ack> responseObserver) {
        SyncBanks.SendAmountIntentRequest newRequest = sendAmountIntentRequestBuilder(request);
        responseObserver.onNext(buildAck(newRequest.getTimestamp()));
        responseObserver.onCompleted();
        logger.info("SendAmountSync: Sent Send Amount Intent with TS: " + newRequest.getTimestamp());
        for(SyncBanksServiceGrpc.SyncBanksServiceBlockingStub stub: SyncBanksStubs)
        {
            stub.sendAmountIntent(newRequest);
        }
    }

    @Override
    public void sendAmountIntent(SyncBanks.SendAmountIntentRequest request, StreamObserver<Bank.Ack> responseObserver) {
        responseObserver.onNext(buildAck());
        responseObserver.onCompleted();

        // receive intent to send amount

        logger.info("Send Amount: Got Intent");

        Bank.SendAmountResponse.Status status = null;

        if(sendAmountIntents.containsKey(request.getTimestamp()) || request.getTimestamp() <= this.timestamp) {
            status = Bank.SendAmountResponse.Status.INVALID_TIMESTAMP;
        } else {
            this.timestamp = request.getTimestamp();
        }

        sendAmountIntents.computeIfAbsent(request.getTimestamp(), k -> new ArrayList<>());
        sendAmountIntents.get(request.getTimestamp()).add(new sendAmountIntent(request.getTimestamp(), request.getSendAmountRequest()));

        // if timestamp is valid check status if amount can be sent
        if(status == null) status = sendAmountStatus(request.getSendAmountRequest());

        // send status to all other servers
        SyncBanks.SendAmountStatusRequest.Builder statusRequest = SyncBanks.SendAmountStatusRequest.newBuilder();
        statusRequest.setSendAmountRequest(request.getSendAmountRequest());
        statusRequest.setSendAmountResponse(sendAmountResponseBuilder(status));
        statusRequest.setTimestamp(request.getTimestamp());

        for(SyncBanksServiceGrpc.SyncBanksServiceBlockingStub stub: SyncBanksStubs)
        {
            stub.sendAmountStatus(statusRequest.build());
        }
    }

    @Override
    public synchronized void sendAmountStatus(SyncBanks.SendAmountStatusRequest request, StreamObserver<Bank.Ack> responseObserver) {
        responseObserver.onNext(buildAck());
        responseObserver.onCompleted();
        logger.info("Send Amount: Got Status");

        sendAmountIntent currentIntent = null;

        while(currentIntent == null) {
            for (sendAmountIntent intent : sendAmountIntents.get(request.getTimestamp())) {
                if (intent.getRequest().equals(request.getSendAmountRequest())) {
                    currentIntent = intent;
                    break;
                }
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        // add to status array of this send amount intent
        currentIntent.addStatus(request.getSendAmountResponse().getStatus());

        // check if majority was achieved
        if(currentIntent.hasMajority(totalServers)) {
            logger.info("Send Amount: Majority Achieved");
            currentIntent.majorityChecked();
            if(currentIntent.getMajority()==Bank.SendAmountResponse.Status.INVALID_TIMESTAMP) {
                logger.info("Send Amount: Invalid Timestamp");
                for (SyncBanks.SendAmountIntentRequest intentRequest : sendAmountOriginalsSync) {
                    if (intentRequest.getSendAmountRequest().equals(request.getSendAmountRequest())) {
                        Bank.SendAmountSync.Builder syncResponse = Bank.SendAmountSync.newBuilder();
                        syncResponse.setSendAmountResponse(sendAmountResponseBuilder(request.getSendAmountResponse().getStatus()));
                        syncResponse.setTimestamp(currentIntent.getTimestamp());
                        BankStub.sendAmountSyncRequest(syncResponse.build());
                    }
                }
                return;
            }
            sendAmountResponses.put(request.getTimestamp(), request.getSendAmountResponse());
            // if so, apply
            if(currentIntent.getMajority()==Bank.SendAmountResponse.Status.SUCCESS) {
                logger.info("Send Amount: Applying Send Amount");
                sendAmount(currentIntent.getRequest());
            }
            // send apply request to all other servers
            SyncBanks.SendAmountAppliedRequest.Builder appliedRequest = SyncBanks.SendAmountAppliedRequest.newBuilder();
            appliedRequest.setSendAmountRequest(currentIntent.getRequest());
            appliedRequest.setTimestamp(currentIntent.getTimestamp());

            for(SyncBanksServiceGrpc.SyncBanksServiceBlockingStub stub: SyncBanksStubs)
            {
                stub.sendAmountApplied(appliedRequest.build());
            }
        }
    }

    @Override
    public void sendAmountApplied(SyncBanks.SendAmountAppliedRequest request, StreamObserver<Bank.Ack> responseObserver) {
        responseObserver.onNext(buildAck());
        responseObserver.onCompleted();
        logger.info("Send Amount: Got Applied");
        // add to applied array of this send amount applied
        sendAmountAppliedCounter.merge(request, 1, Integer::sum);
        // check if majority was achieved
        boolean found = false;
        for (SyncBanks.SendAmountIntentRequest intentRequest : sendAmountOriginalsSync) {
            if (intentRequest.getSendAmountRequest().equals(request.getSendAmountRequest())) {
                found = true;
                break;
            }
        }
        if(!found) return;

        if(totalServers %2==0 ? sendAmountAppliedCounter.get(request)>=(Math.ceil((double)totalServers/2)+1) : sendAmountAppliedCounter.get(request)>=(Math.ceil((double)totalServers/2))) {
            logger.info("Send Amount: Applied Majority");
            // if so, send to client the requests result
            Bank.SendAmountSync.Builder syncResponse = Bank.SendAmountSync.newBuilder();
            syncResponse.setSendAmountResponse(sendAmountResponses.get(request.getTimestamp()));
            syncResponse.setTimestamp(request.getTimestamp());
            BankStub.sendAmountSyncRequest(syncResponse.build());
        }
    }

    // ***** Receive Amount

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

    public void receiveAmount(Bank.ReceiveAmountRequest request) {
        PublicKey destinationKey = null;
        PublicKey sourceKey = null;
        try {
            sourceKey = Crypto.decodePublicKey(request.getTransaction().getSourcePublicKey());
            destinationKey = Crypto.decodePublicKey(request.getTransaction().getDestinationPublicKey());
        } catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
            // Should never happen
            e.printStackTrace();
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
    }

    public Bank.ReceiveAmountResponse receiveAmountResponseBuilder(Bank.ReceiveAmountResponse.Status status) {
        Bank.ReceiveAmountResponse.Builder request = Bank.ReceiveAmountResponse.newBuilder();
        request.setStatus(status);

        return request.build();
    }

    public SyncBanks.ReceiveAmountIntentRequest receiveAmountIntentRequestBuilder(SyncBanks.ReceiveAmountIntentRequest request) {
        SyncBanks.ReceiveAmountIntentRequest.Builder newRequest = SyncBanks.ReceiveAmountIntentRequest.newBuilder();
        newRequest.setReceiveAmountRequest(request.getReceiveAmountRequest());
        newRequest.setTimestamp(this.timestamp + 1);


        receiveAmountOriginalsSync.add(newRequest.build());
        return newRequest.build();
    }

    @Override
    public void receiveAmountSync(SyncBanks.ReceiveAmountIntentRequest request, StreamObserver<Bank.Ack> responseObserver) {
        SyncBanks.ReceiveAmountIntentRequest newRequest = receiveAmountIntentRequestBuilder(request);

        responseObserver.onNext(buildAck(newRequest.getTimestamp()));
        responseObserver.onCompleted();

        logger.info("ReceiveAmountSync: Sent Receive Amount Intent with TS: " + newRequest.getTimestamp());

        for(SyncBanksServiceGrpc.SyncBanksServiceBlockingStub stub: SyncBanksStubs)
        {
            stub.receiveAmountIntent(newRequest);
        }
    }

    @Override
    public void receiveAmountIntent(SyncBanks.ReceiveAmountIntentRequest request, StreamObserver<Bank.Ack> responseObserver) {
        responseObserver.onNext(buildAck());
        responseObserver.onCompleted();

        // receive intent to send amount

        logger.info("Receive Amount: Got Intent");

        Bank.ReceiveAmountResponse.Status status = null;

        if(receiveAmountIntents.containsKey(request.getTimestamp()) || request.getTimestamp() <= this.timestamp) {
            status = Bank.ReceiveAmountResponse.Status.INVALID_TIMESTAMP;
        } else {
            this.timestamp = request.getTimestamp();
        }

        receiveAmountIntents.computeIfAbsent(request.getTimestamp(), k -> new ArrayList<>());
        receiveAmountIntents.get(request.getTimestamp()).add(new receiveAmountIntent(request.getTimestamp(), request.getReceiveAmountRequest()));

        // if timestamp is valid check status if amount can be received
        if(status == null) status = receiveAmountStatus(request.getReceiveAmountRequest());

        // send status to all other servers
        SyncBanks.ReceiveAmountStatusRequest.Builder statusRequest = SyncBanks.ReceiveAmountStatusRequest.newBuilder();
        statusRequest.setReceiveAmountRequest(request.getReceiveAmountRequest());
        statusRequest.setReceiveAmountResponse(receiveAmountResponseBuilder(status));
        statusRequest.setTimestamp(request.getTimestamp());

        for(SyncBanksServiceGrpc.SyncBanksServiceBlockingStub stub: SyncBanksStubs)
        {
            stub.receiveAmountStatus(statusRequest.build());
        }
    }

    @Override
    public synchronized void receiveAmountStatus(SyncBanks.ReceiveAmountStatusRequest request, StreamObserver<Bank.Ack> responseObserver) {
        responseObserver.onNext(buildAck());
        responseObserver.onCompleted();

        logger.info("Receive Amount: Got Status");
        receiveAmountIntent currentIntent = null;

        while(currentIntent == null) {
            for (receiveAmountIntent intent : receiveAmountIntents.get(request.getTimestamp())) {
                if (intent.getRequest().equals(request.getReceiveAmountRequest())) {
                    currentIntent = intent;
                    break;
                }
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        // add to status array of this receive amount intent
        currentIntent.addStatus(request.getReceiveAmountResponse().getStatus());
        logger.info("Receive Amount: Checking Majority");
        // check if majority was achieved
        if(currentIntent.hasMajority(totalServers)) {
            logger.info("Receive Amount: Majority Achieved");
            currentIntent.majorityChecked();
            if(currentIntent.getMajority()==Bank.ReceiveAmountResponse.Status.INVALID_TIMESTAMP) {
                logger.info("Receive Amount: Invalid Timestamp");
                for (SyncBanks.ReceiveAmountIntentRequest intentRequest : receiveAmountOriginalsSync) {
                    if (intentRequest.getReceiveAmountRequest().equals(request.getReceiveAmountRequest())) {
                        Bank.ReceiveAmountSync.Builder syncResponse = Bank.ReceiveAmountSync.newBuilder();
                        syncResponse.setReceiveAmountResponse(receiveAmountResponseBuilder(request.getReceiveAmountResponse().getStatus()));
                        syncResponse.setTimestamp(currentIntent.getTimestamp());
                        BankStub.receiveAmountSyncRequest(syncResponse.build());
                    }
                }
                return;
            }

            receiveAmountResponses.put(request.getTimestamp(), request.getReceiveAmountResponse());
            // if so, apply
            if(currentIntent.getMajority()==Bank.ReceiveAmountResponse.Status.SUCCESS) {
                logger.info("Receive Amount: Executing");
                receiveAmount(currentIntent.getRequest());
            }
            // send apply request to all other servers
            SyncBanks.ReceiveAmountAppliedRequest.Builder appliedRequest = SyncBanks.ReceiveAmountAppliedRequest.newBuilder();
            appliedRequest.setReceiveAmountRequest(currentIntent.getRequest());
            appliedRequest.setTimestamp(currentIntent.getTimestamp());

            for(SyncBanksServiceGrpc.SyncBanksServiceBlockingStub stub: SyncBanksStubs)
            {
                stub.receiveAmountApplied(appliedRequest.build());
            }
        }
    }

    @Override
    public void receiveAmountApplied(SyncBanks.ReceiveAmountAppliedRequest request, StreamObserver<Bank.Ack> responseObserver) {
        responseObserver.onNext(buildAck());
        responseObserver.onCompleted();
        logger.info("Receive Amount: Got Applied");
        // add to applied array of this send amount applied
        receiveAmountAppliedCounter.merge(request, 1, Integer::sum);
        // check if majority was achieved

        boolean found = false;
        for (SyncBanks.ReceiveAmountIntentRequest intentRequest : receiveAmountOriginalsSync) {
            if (intentRequest.getReceiveAmountRequest().equals(request.getReceiveAmountRequest())) {
                found = true;
                break;
            }
        }
        if(!found) return;

        if(totalServers %2==0 ? receiveAmountAppliedCounter.get(request)>=(Math.ceil((double)totalServers/2)+1) : receiveAmountAppliedCounter.get(request)>=(Math.ceil((double)totalServers/2))) {
            logger.info("Receive Amount: Applied Majority");
            // if so, send to client the requests result
            Bank.ReceiveAmountSync.Builder syncResponse = Bank.ReceiveAmountSync.newBuilder();
            syncResponse.setReceiveAmountResponse(receiveAmountResponses.get(request.getTimestamp()));
            syncResponse.setTimestamp(request.getTimestamp());
            BankStub.receiveAmountSyncRequest(syncResponse.build());
        }
    }

    // ***** Check Account
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
        logger.info("Got Check Account Sync");
        CheckAccountIntent intent = new CheckAccountIntent();
        for (SyncBanksServiceGrpc.SyncBanksServiceBlockingStub stub : SyncBanksStubs) {
            // request all values from all servers
            SyncBanks.CheckAccountSyncResponse responseSync = stub.checkAccountSync(request);
            if (intent.addResponse(responseSync.getTimestamp(), responseSync.getCheckAccountResponse(), totalServers)) {
                logger.info("Check Account: Got Majority, sending to client");
                responseObserver.onNext(intent.getMajority());
                responseObserver.onCompleted();
            }
        }
    }

    @Override
    public void checkAccountSync(Bank.CheckAccountRequest request, StreamObserver<SyncBanks.CheckAccountSyncResponse> responseObserver) {
        SyncBanks.CheckAccountSyncResponse.Builder SyncResponse = SyncBanks.CheckAccountSyncResponse.newBuilder();
        Bank.CheckAccountResponse.Builder response = Bank.CheckAccountResponse.newBuilder();

        synchronized (bank) {
            Bank.CheckAccountResponse.Status status = checkAccountStatus(request);
            logger.info("Got check account. Status: {}", status);

            response.setStatus(status);

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
                    SyncResponse.setCheckAccountResponse(response.build());
                    SyncResponse.setTimestamp(timestamp);
                    responseObserver.onNext(SyncResponse.build());
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

            SyncResponse.setCheckAccountResponse(response.build());
            SyncResponse.setTimestamp(this.timestamp);
            responseObserver.onNext(SyncResponse.build());
            responseObserver.onCompleted();
            logger.info("Check Account answered");
        }
    }
    //***** Audit

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
        logger.info("Got Audit Sync");
        AuditIntent intent = new AuditIntent();
        for (SyncBanksServiceGrpc.SyncBanksServiceBlockingStub stub : SyncBanksStubs) {
            // request all values from all servers
            SyncBanks.AuditSyncResponse responseSync = stub.auditSync(request);
            logger.info(request.toString());
            if (intent.addResponse(responseSync.getTimestamp(), responseSync.getAuditResponse(), totalServers)) {
                responseObserver.onNext(intent.getMajority());
                responseObserver.onCompleted();
            }
        }
    }

    @Override
    public void auditSync(Bank.AuditRequest request, StreamObserver<SyncBanks.AuditSyncResponse> responseObserver) {
        SyncBanks.AuditSyncResponse.Builder SyncResponse = SyncBanks.AuditSyncResponse.newBuilder();
        Bank.AuditResponse.Builder response = Bank.AuditResponse.newBuilder();

        synchronized (bank) {
            Bank.AuditResponse.Status status = auditStatus(request);

            logger.info("Got request to audit account. Status {}", status.name());

            response = Bank.AuditResponse.newBuilder().setStatus(status);

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
                    SyncResponse.setAuditResponse(response.build());
                    SyncResponse.setTimestamp(timestamp);
                    responseObserver.onNext(SyncResponse.build());
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

            SyncResponse.setAuditResponse(response.build());
            SyncResponse.setTimestamp(this.timestamp);
            responseObserver.onNext(SyncResponse.build());
            responseObserver.onCompleted();
            logger.info("Audit answered");
        }
    }

    //***** Nonce Negotiation

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
        }
    }
}
