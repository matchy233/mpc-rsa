package mpc.project;

import io.grpc.Context;
import io.grpc.stub.StreamObserver;
import mpc.project.util.RpcUtility;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.concurrent.Semaphore;

public class ManagerRPCSender {

    final private ManagerMain manager;
    private WorkerServiceGrpc.WorkerServiceStub[] stubs;

    public ManagerRPCSender(ManagerMain manager) {
        this.manager = manager;
    }

    public void setStubs(WorkerServiceGrpc.WorkerServiceStub[] stubs) {
        this.stubs = stubs;
    }

    public void sendFormNetworkRequest(int id, String midString) {
        StdRequest request = RpcUtility.Request.newStdRequest(id, midString);
        Context ctx = Context.current().fork();
        ctx.run(() -> {
            stubs[id - 1].formNetwork(request, new StreamObserver<StdResponse>() {
                @Override
                public void onNext(StdResponse response) {
                    System.out.println("received by " + response.getId());
                }

                @Override
                public void onError(Throwable t) {
                    System.out.println("RPC error: " + t.getMessage());
                    System.exit(-1);
                }

                @Override
                public void onCompleted() {
                    manager.getDataReceiver().receiveNetworkFormingResponse();
                }
            });
        });
    }

    public void sendPrimalityTestRequest(int id) {
        StdRequest request = RpcUtility.Request.newStdRequest(0);
        stubs[id - 1].primalityTest(request, new StreamObserver<PrimalityTestResponse>() {
            @Override
            public void onNext(PrimalityTestResponse response) {
                boolean primalityTestResult = (response.getId() == 1);
                manager.getDataReceiver().receivePrimalityTestResult(primalityTestResult);
                System.out.println("received by " + response.getId());
            }

            @Override
            public void onError(Throwable t) {
                System.out.println("Primality test RPC error: " + t.getMessage());
                System.exit(-1);
            }

            @Override
            public void onCompleted() {
            }
        });
    }

    public void sendGeneratePrivateKeyRequest(int id) {
        stubs[id - 1].generatePrivateKey(RpcUtility.Request.newStdRequest(0), new StreamObserver<StdResponse>() {
            @Override
            public void onNext(StdResponse response) {
                manager.getDataReceiver().receivePrivateKeyGenerationResponse();
            }

            @Override
            public void onError(Throwable t) {
                System.out.println("generate Key RPC error: " + t.getMessage());
                System.exit(-1);
            }

            @Override
            public void onCompleted() {
            }
        });
    }

    public void sendModulusGenerationRequest(int id, int keyBitLength, BigInteger randomPrime) {
        StdRequest request = RpcUtility.Request.newStdRequest(keyBitLength, randomPrime);
        stubs[id - 1].generateModulusPiece(request, new StreamObserver<StdResponse>() {
            @Override
            public void onNext(StdResponse response) {
                manager.getDataReceiver().receiveModulusGenerationResponse();
            }

            @Override
            public void onError(Throwable t) {
                System.out.println("RPC error: " + t.getMessage());
                System.exit(-1);
            }

            @Override
            public void onCompleted() {
            }
        });
    }

    public void sendDecryptionRequest(int id, String encryptedMessage, String[] resultBucket) {
        stubs[id - 1].decrypt(RpcUtility.Request.newStdRequest(id, encryptedMessage),
                new StreamObserver<StdResponse>() {
                    @Override
                    public void onNext(StdResponse response) {
                        int id = response.getId();
                        String shadow = new String(response.getContents().toByteArray());
                        manager.getDataReceiver().receiveDecryptionResult(id, shadow, resultBucket);
                    }

                    @Override
                    public void onError(Throwable t) {
                        System.out.println("decryption error: " + t.getMessage());
                    }

                    @Override
                    public void onCompleted() {
                    }
                });
    }
}
