package pt.ulisboa.tecnico.sec.candeeiros.server;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pt.ulisboa.tecnico.sec.candeeiros.Bank;
import pt.ulisboa.tecnico.sec.candeeiros.BankServiceGrpc;
import pt.ulisboa.tecnico.sec.candeeiros.SyncBanks;
import pt.ulisboa.tecnico.sec.candeeiros.SyncBanksServiceGrpc;
import pt.ulisboa.tecnico.sec.candeeiros.server.model.BftBank;
import pt.ulisboa.tecnico.sec.candeeiros.server.model.openAccountIntent;
import pt.ulisboa.tecnico.sec.candeeiros.shared.Crypto;
import pt.ulisboa.tecnico.sec.candeeiros.shared.KeyManager;
import pt.ulisboa.tecnico.sec.candeeiros.shared.Signatures;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
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
}
