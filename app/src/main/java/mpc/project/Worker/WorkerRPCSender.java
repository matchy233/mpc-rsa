package mpc.project.Worker;

import io.grpc.Context;
import io.grpc.stub.StreamObserver;
import mpc.project.*;
import mpc.project.util.RpcUtility;

import java.math.BigInteger;

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

    public void broadcastModulusGenerationRequest(int bitLength, BigInteger randomPrime, long workflowID) {
        for (int id = 1; id <= worker.getClusterSize(); id++) {
            StdRequest request = RpcUtility.Request.newStdRequest(bitLength, randomPrime, workflowID);
            int finalId = id;
            stubs[id - 1].generateModulusPiece(request, new StreamObserver<StdResponse>() {
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

    public void sendPQH(int id, BigInteger p, BigInteger q, BigInteger h, long workflowID) {
        ExchangePrimespqhRequest request = RpcUtility.Request.newExchangePrimesRequest(worker.getId(), p, q, h, workflowID);
        Context ctx = Context.current().fork();
        ctx.run(() -> stubs[id - 1].exchangePrimesPQH(request, new StreamObserver<>() {
            @Override
            public void onNext(StdResponse response) {
            }

            @Override
            public void onError(Throwable t) {
//                t.printStackTrace();
                System.out.println("exchangePQH RPC error for " + id + " : " + t.getMessage());
                System.exit(-1);
            }

            @Override
            public void onCompleted() {
//                System.out.println("sent!");
            }
        }));
    }

    public void sendNPiece(int id, BigInteger nPiece, long workflowID) {
        StdRequest request = RpcUtility.Request.newStdRequest(worker.getId(), nPiece, workflowID);
        Context ctx = Context.current().fork();
        ctx.run(() -> stubs[id - 1].exchangeNPiece(request, new StreamObserver<>() {
            @Override
            public void onNext(StdResponse response) {
            }

            @Override
            public void onError(Throwable t) {
//                t.printStackTrace();
                System.out.println("sendNPiece RPC error for " + id + " : " + t.getMessage());
                System.exit(-1);
            }

            @Override
            public void onCompleted() {
//                System.out.println("sent!");
            }
        }));
    }

    public void sendPrimalityTestRequest(int id, BigInteger g, long workflowID) {
        StdRequest request = RpcUtility.Request.newStdRequest(worker.getId(), g, workflowID);
        stubs[id - 1].primalityTest(request, new StreamObserver<>() {
            @Override
            public void onNext(PrimalityTestResponse value) {
                int id = value.getId();
                BigInteger v = new BigInteger(value.getV().toByteArray());
                worker.getDataReceiver().receiveVerificationFactor(id, v, workflowID);
            }

            @Override
            public void onError(Throwable t) {
//                t.printStackTrace();
                System.out.println("primalityTest to Guests RPC Error for " + id + " : " + t.getMessage());
                System.exit(-1);
            }

            @Override
            public void onCompleted() {
                worker.getDataReceiver().countVerificationFactor(workflowID);
            }
        });
    }

    public void sendGamma(int id, BigInteger gamma, long workflowID) {
        StdRequest request = RpcUtility.Request.newStdRequest(worker.getId(), gamma, workflowID);
        Context ctx = Context.current().fork();
        ctx.run(() -> stubs[id - 1].exchangeGamma(request, new StreamObserver<>() {
            @Override
            public void onNext(StdResponse response) {
//                System.out.println("received by " + response.getId());
            }

            @Override
            public void onError(Throwable t) {
//                t.printStackTrace();
                System.out.println("sendGamma RPC error for " + id + " : " + t.getMessage());
                System.exit(-1);
            }

            @Override
            public void onCompleted() {
            }
        }));
    }

    public void sendGammaSum(int id, BigInteger gammaSum, long workflowID) {
        StdRequest request = RpcUtility.Request.newStdRequest(worker.getId(), gammaSum, workflowID);
        Context ctx = Context.current().fork();
        ctx.run(() -> stubs[id - 1].exchangeGammaSum(request, new StreamObserver<>() {
            @Override
            public void onNext(StdResponse response) {
//                System.out.println("received by " + response.getId());
            }

            @Override
            public void onError(Throwable t) {
//                t.printStackTrace();
                System.out.println("sendGamma RPC error for " + id + " : " + t.getMessage());
                System.exit(-1);
            }

            @Override
            public void onCompleted() {
            }
        }));
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
//                        t.printStackTrace();
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
