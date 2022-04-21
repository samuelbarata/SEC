package pt.ulisboa.tecnico.sec.candeeiros.server;

import io.grpc.stub.StreamObserver;
import pt.ulisboa.tecnico.sec.candeeiros.Bank;
import pt.ulisboa.tecnico.sec.candeeiros.SyncBanks;
import pt.ulisboa.tecnico.sec.candeeiros.SyncBanksServiceGrpc;
import pt.ulisboa.tecnico.sec.candeeiros.server.model.BftBank;
import pt.ulisboa.tecnico.sec.candeeiros.shared.Crypto;
import pt.ulisboa.tecnico.sec.candeeiros.shared.Signatures;

import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.HashMap;

public class SyncBanksServiceImpl extends SyncBanksServiceGrpc.SyncBanksServiceImplBase {

    private int timestamp;
    private final BftBank bank;
    private final PrivateKey privateKey;
    private HashMap<SyncBanks.OpenAccountIntentRequest, ArrayList<Integer>> openAccountIntents;

    SyncBanksServiceImpl(BftBank bank, PrivateKey privateKey) {
        super();
        timestamp = 0;
        this.bank = bank;
        this.privateKey = privateKey;
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
    @Override
    public void openAccountIntent(SyncBanks.OpenAccountIntentRequest request, StreamObserver<Bank.Ack> responseObserver) {
        // receive intent to open account
        openAccountIntents.put(request, new ArrayList<>());

        // check status if account can be opened
        Bank.OpenAccountResponse.Status status = openAccountStatus(request.getOpenAccountRequest());


        // send status to all servers
        
    }

    @Override
    public void openAccountStatus(SyncBanks.OpenAccountStatusRequest request, StreamObserver<Bank.Ack> responseObserver) {
        // add to status array of this open account intent

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
