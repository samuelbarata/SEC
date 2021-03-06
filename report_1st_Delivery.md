# SEC Project Report 1st Delivery

## Security Design Decisions

The goal was to guarantee authenticity, integrity and, when applicable, non repudiation and freshness.
We intentionally omit confidentiality guarantees because the system is, by design, open to the public.

To this end, we use RSA signatures with SHA257 digest to sign the necessary messages, as well both random Nonces and "sequential" Nonces.

## Program Architecture

The server is a standalone executable that receives the port, a file with its private key, and a location for the ledger file.

The client is a library to be used by other programs. It delegates the responsibility of managing the keys to the calling program.
It gives an interface through which the calling program can interact with the server.

A byzantine client is included that has functions to simulate attacks on the service.

## Remote Procedure Calls Arguments

- `OpenAccount(challengeNonce, publicKey, signature) -> (challengeNonce, status, signature)`
- `NonceNegotiation(challengeNonce, publicKey, signature) -> (challengeNonce, status, nonce, signature)`
- `SendAmount(nonce, transaction, signature) -> (nonce, status, signature)`
- `ReceiveAmount(nonce, transaction, accept, signature) -> (nonce, status, signature)` - accept is a boolean where true will accept the transaction, false will reject it
- `CheckAccount(challengeNonce, publicKey) -> (challengeNonce, status, balance, transactions, signature)`
- `Audit(challengeNonce, publicKey) -> (challengeNonce, status, transactions, signature)`

Where signatures, public keys, and nonces are gRPC bytestrings, and transactions are either Transactions or NonRepudiableTransactions, in Audit and CheckAccount.

## Signatures

Every message sent by the server is signed. This guarantees authenticity, integrity and non-repudiation for the server's messages.
Furthermore, the relevant messages from the client are also signed:
- OpenAccount
- NonceNegotiation
- SendAmount
- ReceiveAmount

Messages that are not associated with an account are not signed, as they can be sent by anyone, thus authentication is unnecessary.
On those messages, integrity is effectively guaranteed by the fact that the response includes information that allows the client to verify that the server got the right message.

The signatures of messages that create or accept transactions are stored by the server, such that it can guarantee non-repudiation of those transactions.

## Nonces

Every message includes some form of nonce.

Messages that do not require authentication or that happen before authentication use a challenge system: the client simply generates a random nonce, and the server echoes it back.
Non-duplication is guaranteed by the fact that the nonce is generated with enough bits that the chance of nonce duplication is statistically negligible.

SendAmount and ReceiveAmount, however, use a sequence counting system starting on a random number (which is effectively another form of nonce).
Each account is given a random starting nonce and every message sent by that client increments that count.
A message is only valid if the provided nonce is the one stored by the client incremented by one.

## DOS Protection

Denial of Service protection is implemented by the server only on the functions that require a change of state:
- OpenAccount
- SendAmount
- ReceieveAmount

We implemented DOS protection by blocking the account makinging the request and not the ip of the atacker just for simplicity. We consider a burst of requests to be a DOS attack if it was under 400ms between requests.


## Crash Recovery

The server creates and maintains a ledger file with the list of actions performed on the server.
This file is only ever appended to; this way, we can guarantee that in the event of a crash, assuming the OS maintains it guarantees, only the last line can be corrupted.
Every line ends with a linefeed ('\n').

On start, if a ledger file exists, the server will read it, line by line, and re-enact the execution of every action thus far.
If the last line does not include a linefeed, it is considered corrupt and deleted from the file.
This way, in the event of a crash, only ever the last performed action can be eliminated.

If a line cannot be parsed other than the last line, the server will exit, as this implies a corrupt ledger and cannot be recovered from. 