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
import pt.ulisboa.tecnico.sec.candeeiros.shared.Signatures;

import java.io.IOException;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class SyncBanksServiceImpl extends SyncBanksServiceGrpc.SyncBanksServiceImplBase {

    private static final Logger logger = LoggerFactory.getLogger(BankServiceImpl.class);

    private int timestamp;
    private final BftBank bank;
    private final KeyManager keyManager;
    private final HashMap<Integer, openAccountIntent> openAccountIntents;
    private final HashMap<Integer, Bank.OpenAccountResponse> openAccountResponses;

    //Communication between SyncBanks
    private List<String> SyncBanksTargets;
    private ArrayList<ManagedChannel> SyncBanksManagedChannels;
    private ArrayList<SyncBanksServiceGrpc.SyncBanksServiceBlockingStub> SyncBanksStubs;

    //Communication between Banks
    private String BankTarget;
    private ManagedChannel BankManagedChannel;
    private BankServiceGrpc.BankServiceBlockingStub BankStub;

    //Applied HashMaps
    private HashMap<SyncBanks.OpenAccountAppliedRequest, Integer> openAppliedCounter;

    //***
    private int totalServers;

    SyncBanksServiceImpl(String ledgerFileName, KeyManager keyManager, int totalServers, String bankTarget) throws IOException {
        super();
        timestamp = 0;
        this.bank = new BftBank(ledgerFileName);
        this.BankTarget = bankTarget;

        this.openAccountIntents = new HashMap<>();
        this.openAccountResponses = new HashMap<>();

        this.SyncBanksManagedChannels = new ArrayList<>();
        this.SyncBanksTargets = new ArrayList<>();
        this.SyncBanksTargets.add(bankTarget);
        this.SyncBanksStubs = new ArrayList<>();

        this.openAppliedCounter = new HashMap<>();

        this.totalServers = totalServers;
        this.keyManager = keyManager;
        CreateStubs();
        System.out.println(Math.ceil(totalServers/2));
    }

    public void CreateStubs()
    {
        ManagedChannel channel;

        for(String target: SyncBanksTargets) {
            channel = ManagedChannelBuilder.forTarget(target).usePlaintext().build();
            SyncBanksManagedChannels.add(channel);
            SyncBanksStubs.add(SyncBanksServiceGrpc.newBlockingStub(channel));
        }

        BankManagedChannel = ManagedChannelBuilder.forTarget(BankTarget).usePlaintext().build();
        BankStub = BankServiceGrpc.newBlockingStub(BankManagedChannel);

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
        Bank.OpenAccountResponse.Builder request = Bank.OpenAccountResponse.newBuilder();
        request.setStatus(status);


        return request.build();
    }

    public Bank.Ack buildAck() {
        return Bank.Ack.newBuilder().build();
    }

    @Override
    public void openAccountIntent(SyncBanks.OpenAccountIntentRequest request, StreamObserver<Bank.Ack> responseObserver) {
        responseObserver.onNext(buildAck());
        responseObserver.onCompleted();
        // receive intent to open account
        System.out.println("Got Intent");
        openAccountIntents.put(request.getTimestamp(), new openAccountIntent(request.getTimestamp(), request.getOpenAccountRequest()));

        // check status if account can be opened
        Bank.OpenAccountResponse.Status status = openAccountStatus(request.getOpenAccountRequest());

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
    public void openAccountStatus(SyncBanks.OpenAccountStatusRequest request, StreamObserver<Bank.Ack> responseObserver) {
        responseObserver.onNext(buildAck());
        responseObserver.onCompleted();
        System.out.println("Got Status");
        openAccountIntent currentIntent = openAccountIntents.get(request.getTimestamp());
        //TODO what if the currentIntent is null/doesn't exist?

        // add to status array of this open account intent
        currentIntent.addStatus(request.getOpenAccountResponse().getStatus());

        // check if majority was achieved
        if(currentIntent.hasMajority(totalServers)) {
            if(currentIntent.getMajority()==Bank.OpenAccountResponse.Status.SUCCESS) {
                openAccountResponses.put(request.getTimestamp(), request.getOpenAccountResponse());
                // if so, apply
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
    }

    @Override
    public void openAccountApplied(SyncBanks.OpenAccountAppliedRequest request, StreamObserver<Bank.Ack> responseObserver) {
        responseObserver.onNext(buildAck());
        responseObserver.onCompleted();
        System.out.println("Got Applied");
        // add to applied array of this open account applied
        if(openAppliedCounter.get(request)!=null) openAppliedCounter.put(request, openAppliedCounter.get(request)+1);
        else openAppliedCounter.put(request, 1);
        // check if majority was achieved
        if(openAppliedCounter.get(request)>=(Math.ceil(totalServers/2))) {
            System.out.println("Applied Majority");
            // if so, send to client the requests result
            Bank.OpenAccountSync.Builder syncResponse = Bank.OpenAccountSync.newBuilder();
            syncResponse.setOpenAccountResponse(openAccountResponses.get(request.getTimestamp()));
            syncResponse.setTimestamp(request.getTimestamp());
            BankStub.openAccountSyncRequest(syncResponse.build());
        }
    }

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
        CheckAccountIntent intent = new CheckAccountIntent();
        for (SyncBanksServiceGrpc.SyncBanksServiceBlockingStub stub : SyncBanksStubs) {
            // request all values from all servers
            SyncBanks.CheckAccountSyncResponse responseSync = stub.checkAccountSync(request);
            if (intent.addResponse(responseSync.getTimestamp(), responseSync.getCheckAccountResponse(), totalServers)) {
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
            SyncResponse.setTimestamp(timestamp);
            responseObserver.onNext(SyncResponse.build());
            responseObserver.onCompleted();
        }
    }
}
