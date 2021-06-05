package mpc.project.Manager;

import io.grpc.Context;
import io.grpc.stub.StreamObserver;
import mpc.project.PrimalityTestResponse;
import mpc.project.StdRequest;
import mpc.project.StdResponse;
import mpc.project.WorkerServiceGrpc;
import mpc.project.util.RpcUtility;

import java.math.BigInteger;

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
                    manager.getRpcSender().broadcastShutDownWorkerRequest(t.getMessage());
                    System.exit(-1);
                }

                @Override
                public void onCompleted() {
                    manager.getDataReceiver().receiveNetworkFormingResponse();
                }
            });
        });
    }

    public void sendHostPrimalityTestRequest(int id, long workflowID) {
        StdRequest request = RpcUtility.Request.newStdRequest(0, workflowID);
        stubs[id - 1].hostPrimalityTest(request, new StreamObserver<StdResponse>() {
            @Override
            public void onNext(StdResponse response) {
                boolean primalityTestResult = (response.getId() == 1);
                manager.getDataReceiver().receivePrimalityTestResult(primalityTestResult, workflowID);
            }

            @Override
            public void onError(Throwable t) {
                System.out.println("Primality test RPC error: " + t.getMessage());
                manager.getRpcSender().broadcastShutDownWorkerRequest(t.getMessage());
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
                manager.getRpcSender().broadcastShutDownWorkerRequest(t.getMessage());
                System.exit(-1);
            }

            @Override
            public void onCompleted() {
            }
        });
    }

    public void sendHostModulusGenerationRequest(int id, int keyBitLength, BigInteger randomPrime, long workflowID) {
        StdRequest request = RpcUtility.Request.newStdRequest(keyBitLength, randomPrime, workflowID);
        stubs[id - 1].hostModulusGeneration(request, new StreamObserver<StdResponse>() {
            @Override
            public void onNext(StdResponse response) {
                BigInteger modulus = new BigInteger(response.getContents().toByteArray());
                manager.getDataReceiver().receiveModulusGenerationResponse(modulus, workflowID);
            }

            @Override
            public void onError(Throwable t) {
                System.out.println("send host modulus generation request error: " + t.getMessage());
                manager.getRpcSender().broadcastShutDownWorkerRequest(t.getMessage());
                System.exit(-1);
            }

            @Override
            public void onCompleted() {
            }
        });
    }

    public void sendAbortModulusGenerationRequest(int id){
        StdRequest request = RpcUtility.Request.newStdRequest(id);
        stubs[id - 1].abortModulusGeneration(request, new StreamObserver<StdResponse>() {
            @Override
            public void onNext(StdResponse response) {
            }

            @Override
            public void onError(Throwable t) {
                System.out.println("send abort modulus generation request error: " + t.getMessage());
                manager.getRpcSender().broadcastShutDownWorkerRequest(t.getMessage());
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

    public void sendShutDownWorkerRequest(int id, String shutDownMessage){
        stubs[id-1].shutDownWorker(RpcUtility.Request.newStdRequest(id, shutDownMessage),
                new StreamObserver<StdResponse>() {
                    @Override
                    public void onNext(StdResponse value) {
                    }

                    @Override
                    public void onError(Throwable t) {
                        System.out.println("send shutDownRequest error: " + t.getMessage());
                    }

                    @Override
                    public void onCompleted() {
                    }
                });
    }

    public void broadcastShutDownWorkerRequest(String shutDownMessage){
        for(int i = 1; i <= manager.getClusterSize(); i++){
            sendShutDownWorkerRequest(i, shutDownMessage);
        }
    }
}
