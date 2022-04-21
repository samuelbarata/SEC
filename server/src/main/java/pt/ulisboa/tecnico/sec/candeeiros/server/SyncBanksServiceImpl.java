package pt.ulisboa.tecnico.sec.candeeiros.server;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import pt.ulisboa.tecnico.sec.candeeiros.Bank;
import pt.ulisboa.tecnico.sec.candeeiros.SyncBanks;
import pt.ulisboa.tecnico.sec.candeeiros.SyncBanksServiceGrpc;
import pt.ulisboa.tecnico.sec.candeeiros.server.model.BftBank;
import pt.ulisboa.tecnico.sec.candeeiros.server.model.openAccountIntent;
import pt.ulisboa.tecnico.sec.candeeiros.shared.Crypto;
import pt.ulisboa.tecnico.sec.candeeiros.shared.Signatures;

import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class SyncBanksServiceImpl extends SyncBanksServiceGrpc.SyncBanksServiceImplBase {

    private int timestamp;
    private final BftBank bank;
    private final PrivateKey privateKey;
    private HashMap<Integer, openAccountIntent> openAccountIntents;

    private List<String> SyncBanksTargets;

    private ArrayList<ManagedChannel> SyncBanksManagedChannels;
    private ArrayList<SyncBanksServiceGrpc.SyncBanksServiceBlockingStub> SyncBanksStubs;

    SyncBanksServiceImpl(BftBank bank, PrivateKey privateKey) {
        super();
        timestamp = 0;
        this.bank = bank;
        this.privateKey = privateKey;
        this.SyncBanksManagedChannels = new ArrayList<>();
        this.SyncBanksTargets = new ArrayList<>();
        this.SyncBanksStubs = new ArrayList<>();
        CreateStubs();
    }

    public void CreateStubs()
    {
        ManagedChannel channel;
        for(String target: SyncBanksTargets)
        {
            channel = ManagedChannelBuilder.forTarget(target).usePlaintext().build();
            SyncBanksManagedChannels.add(channel);
            SyncBanksStubs.add(SyncBanksServiceGrpc.newBlockingStub(channel));
        }
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

    public Bank.OpenAccountResponse openAccountResponseBuilder(Bank.OpenAccountResponse.Status status) {
        Bank.OpenAccountResponse.Builder request = Bank.OpenAccountResponse.newBuilder();
        request.setStatus(status);


        return request.build();
    }

    @Override
    public void openAccountIntent(SyncBanks.OpenAccountIntentRequest request, StreamObserver<Bank.Ack> responseObserver) {
        // receive intent to open account
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
        // add to status array of this open account intent
        openAccountIntents.get(request.getTimestamp()).addStatus(request.getOpenAccountResponse().getStatus());

        // check if majority was achieved

        // if so, open account and send open account applied
    }

    @Override
    public void openAccountApplied(SyncBanks.OpenAccountAppliedRequest request, StreamObserver<Bank.Ack> responseObserver) {
        // add to applied array of this open account applied

        // check if majority was achieved

        // if so, send to client the requests result
    }
}
