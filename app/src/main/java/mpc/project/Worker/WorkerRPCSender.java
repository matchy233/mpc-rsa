package mpc.project.Worker;

import io.grpc.stub.StreamObserver;
import mpc.project.*;
import mpc.project.util.RpcUtility;

import java.math.BigInteger;
import java.util.concurrent.*;

public class WorkerRPCSender {
    private WorkerServiceGrpc.WorkerServiceStub[] stubs;
    private ManagerServiceGrpc.ManagerServiceBlockingStub managerStub;

    public void setManagerStub(ManagerServiceGrpc.ManagerServiceBlockingStub managerStub) {
        this.managerStub = managerStub;
    }

    final private WorkerMain worker;

    public WorkerRPCSender(WorkerMain worker) {
        this.worker = worker;
    }

    public void setStubs(WorkerServiceGrpc.WorkerServiceStub[] stubs) {
        this.stubs = stubs;
    }

    private final Executor senderExecutor = Executors.newCachedThreadPool();

    public void broadcastModulusGenerationRequest(int bitLength, BigInteger randomPrime, long workflowID) {
        ModulusRequest request = RpcUtility.Request.newModulusRequest(
                worker.getId(), bitLength, randomPrime, workflowID
        );
        for (int id = 1; id <= worker.getClusterSize(); id++) {
            int finalId = id;
            stubs[id - 1].generateModulus(request, new StreamObserver<StdResponse>() {
                @Override
                public void onNext(StdResponse response) {
                    int id = response.getId();
                    BigInteger modulus = new BigInteger(response.getContents().toByteArray());
                    worker.getDataReceiver().receiveModulus(modulus, workflowID);
                }

                @Override
                public void onError(Throwable t) {
                    System.out.println("Broadcast modulus generation error, id: " + finalId + ", " + t.getMessage());
                    System.exit(-1);
                }

                @Override
                public void onCompleted() {
                    worker.getDataReceiver().countModulus(workflowID);
                }
            });
        }
    }

    public void broadcastBPieceArr(BigInteger[] bArr, long workflowID) {
        worker.getDataReceiver().receiveBPiece(worker.getId(), bArr[worker.getId() - 1], workflowID);
        senderExecutor.execute(() -> {
            for (int id = 1; id <= worker.getClusterSize(); id++) {
                if (id == worker.getId()) {
                    continue;
                }
                StdRequest request = RpcUtility.Request.newStdRequest(worker.getId(), bArr[id - 1], workflowID);
                int finalId = id;
                stubs[id - 1].initializeBPiece(request, new StreamObserver<>() {
                    @Override
                    public void onNext(StdResponse response) {
                    }

                    @Override
                    public void onError(Throwable t) {
                        System.out.println("initialize B piece RPC error for " + finalId + " : " + t.getMessage());
                        System.exit(-1);
                    }

                    @Override
                    public void onCompleted() {
                    }
                });
            }
        });
    }

    public void broadcastPQHArr(BigInteger[] pArr, BigInteger[] qArr, BigInteger[] hArr, long workflowID) {
        worker.getDataReceiver().receivePHQ(
                worker.getId(), pArr[worker.getId() - 1], qArr[worker.getId() - 1], hArr[worker.getId() - 1], workflowID
        );
        senderExecutor.execute(() -> {
            for (int id = 1; id <= worker.getClusterSize(); id++) {
                if (id == worker.getId()) {
                    continue;
                }
                ExchangePrimespqhRequest request = RpcUtility.Request.newExchangePrimesRequest(
                        worker.getId(), pArr[id - 1], qArr[id - 1], hArr[id - 1], workflowID
                );
                int finalId = id;
                stubs[id - 1].exchangePrimesPQH(request, new StreamObserver<>() {
                    @Override
                    public void onNext(StdResponse response) {
                    }

                    @Override
                    public void onError(Throwable t) {
                        System.out.println("exchangePQH RPC error for " + finalId + " : " + t.getMessage());
                        System.exit(-1);
                    }

                    @Override
                    public void onCompleted() {
                    }
                });
            }
        });
    }

    public void broadcastNPiece(BigInteger nPiece, long workflowID) {
        StdRequest request = RpcUtility.Request.newStdRequest(worker.getId(), nPiece, workflowID);
        worker.getDataReceiver().receiveNPiece(worker.getId(), nPiece, workflowID);
        senderExecutor.execute(() -> {
            for (int id = 1; id <= worker.getClusterSize(); id++) {
                if (id == worker.getId()) {
                    continue;
                }
                int finalId = id;
                stubs[id - 1].exchangeNPiece(request, new StreamObserver<>() {
                    @Override
                    public void onNext(StdResponse response) {
                    }

                    @Override
                    public void onError(Throwable t) {
                        System.out.println("sendNPiece RPC error for " + finalId + " : " + t.getMessage());
                        System.exit(-1);
                    }

                    @Override
                    public void onCompleted() {
                    }
                });
            }
        });
    }

    public void broadcastPrimalityTestRequest(BigInteger g, long workflowID) {
        StdRequest request = RpcUtility.Request.newStdRequest(worker.getId(), g, workflowID);
        for (int id = 1; id <= worker.getClusterSize(); id++) {
            int finalId = id;
            stubs[id - 1].primalityTest(request, new StreamObserver<>() {
                @Override
                public void onNext(PrimalityTestResponse value) {
                    int id = value.getId();
                    BigInteger v = new BigInteger(value.getV().toByteArray());
                    worker.getDataReceiver().receiveVerificationFactor(id, v, workflowID);
                }

                @Override
                public void onError(Throwable t) {
                    System.out.println("primalityTest to Guests RPC Error for " + finalId + " : " + t.getMessage());
                    System.exit(-1);
                }

                @Override
                public void onCompleted() {
                    worker.getDataReceiver().countVerificationFactor(workflowID);
                }
            });
        }
    }

    public void broadcastDarioGamma(BigInteger darioGamma, long workflowID) {
        StdRequest request = RpcUtility.Request.newStdRequest(worker.getId(), darioGamma, workflowID);
        worker.getDataReceiver().receiveDarioGamma(worker.getId(), darioGamma, workflowID);
        senderExecutor.execute(() -> {
            for (int id = 1; id <= worker.getClusterSize(); id++) {
                if (id == worker.getId()) {
                    continue;
                }
                int finalId = id;
                stubs[id - 1].exchangeDarioGamma(request, new StreamObserver<>() {
                    @Override
                    public void onNext(StdResponse response) {
                    }

                    @Override
                    public void onError(Throwable t) {
                        System.out.println("sendGamma RPC error for " + finalId + " : " + t.getMessage());
                        System.exit(-1);
                    }

                    @Override
                    public void onCompleted() {
                    }
                });
            }
        });
    }

    public void sendDecryptRequest(int id, String encryptedMessage, long workflowID) {
        stubs[id - 1].decrypt(RpcUtility.Request.newStdRequest(worker.getId(), encryptedMessage),
                new StreamObserver<>() {
                    @Override
                    public void onNext(StdResponse response) {
                        int id = response.getId();
                        String shadow = new String(response.getContents().toByteArray());
                        worker.getDataReceiver().receiveShadow(id, shadow, workflowID);
                    }

                    @Override
                    public void onError(Throwable t) {
                        System.out.println("trial decryption error: " + t.getMessage());
                        System.exit(-1);
                    }

                    @Override
                    public void onCompleted() {
                        worker.getDataReceiver().countShadow(workflowID);
                    }
                });
    }
}
