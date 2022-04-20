package pt.ulisboa.tecnico.sec.candeeiros.server;

import io.grpc.stub.StreamObserver;
import pt.ulisboa.tecnico.sec.candeeiros.SyncBanks;
import pt.ulisboa.tecnico.sec.candeeiros.SyncBanksServiceGrpc;
import pt.ulisboa.tecnico.sec.candeeiros.server.model.BftBank;

import java.security.PrivateKey;

public class SyncBanksServiceImpl extends SyncBanksServiceGrpc.SyncBanksServiceImplBase {

    private int timestamp;
    private final BftBank bank;
    private final PrivateKey privateKey;

    SyncBanksServiceImpl(BftBank bank, PrivateKey privateKey) {
        super();
        timestamp = 0;
        this.bank = bank;
        this.privateKey = privateKey;
    }
    // ***** Authenticated procedures *****

    // ***** Open Account *****
    @Override
    public void openAccountIntent(SyncBanks.OpenAccountIntentRequest request, StreamObserver<SyncBanks.Ack> responseObserver) {

    }
}
