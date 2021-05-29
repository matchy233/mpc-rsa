package mpc.project;

import io.grpc.Channel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import mpc.project.util.RSA;
import mpc.project.util.RpcUtility;

import java.math.BigInteger;

public class WorkerRPCReceiverService extends WorkerServiceGrpc.WorkerServiceImplBase {
    final private WorkerMain worker;
    private int id;

    public WorkerRPCReceiverService(WorkerMain worker) {
        this.worker = worker;
    }

    @Override
    public void formCluster(StdRequest request, StreamObserver<StdResponse> responseObserver) {
        StdResponse res = RpcUtility.Response.newStdResponse(id);
        responseObserver.onNext(res);
        responseObserver.onCompleted();
        System.out.println("connected to Manager");
    }

    @Override
    public void formNetwork(StdRequest request, StreamObserver<StdResponse> responseObserver) {
        String midString = new String(request.getContents().toByteArray());
        String[] addressBook = midString.split(";");
        StdResponse response = RpcUtility.Response.newStdResponse(id);
        WorkerServiceGrpc.WorkerServiceStub[] stubs = new WorkerServiceGrpc.WorkerServiceStub[addressBook.length];
        System.out.println("received and parsed addressBook: ");
        for (int i = 0; i < addressBook.length; i++) {
            System.out.println(addressBook[i]);
            Channel channel = ManagedChannelBuilder.forTarget(addressBook[i]).usePlaintext().build();
            stubs[i] = WorkerServiceGrpc.newStub(channel);
        }
        worker.getRpcSender().setStubs(stubs);
        worker.setClusterSize(addressBook.length);
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void registerManager(StdRequest request, StreamObserver<StdResponse> responseObserver) {
        worker.setId(request.getId());
        this.id = request.getId();
        String managerUri = new String(request.getContents().toByteArray());
        Channel channel = ManagedChannelBuilder.forTarget(managerUri).usePlaintext().build();
        worker.getRpcSender().setManagerStub(ManagerServiceGrpc.newBlockingStub(channel));
        responseObserver.onNext(RpcUtility.Response.newStdResponse(id));
        responseObserver.onCompleted();
        System.out.println("registered manager at " + managerUri);
    }

    @Override
    public void generateModulusPiece(StdRequest request, StreamObserver<StdResponse> responseObserver) {
        // id is used for bitNum now, not id
        int bitNum = request.getId();
        BigInteger randomPrime = new BigInteger(request.getContents().toByteArray());
        worker.generateModulusPiece(bitNum, randomPrime);
        responseObserver.onNext(RpcUtility.Response.newStdResponse(id));
        responseObserver.onCompleted();
    }

    @Override
    public void exchangePrimesPQH(ExchangePrimespqhRequest request, StreamObserver<StdResponse> responseObserver) {
        int id = request.getId();
        BigInteger p = new BigInteger(request.getP().toByteArray());
        BigInteger q = new BigInteger(request.getQ().toByteArray());
        BigInteger h = new BigInteger(request.getH().toByteArray());
        responseObserver.onNext(RpcUtility.Response.newStdResponse(id));
        responseObserver.onCompleted();
        worker.getDataReceiver().receivePHQ(id, p, q, h);
    }

    @Override
    public void exchangeNPiece(StdRequest request, StreamObserver<StdResponse> responseObserver) {
        int id = request.getId();
        BigInteger nPiece = new BigInteger(request.getContents().toByteArray());
        responseObserver.onNext(RpcUtility.Response.newStdResponse(id));
        responseObserver.onCompleted();
        worker.getDataReceiver().receiveNPiece(id, nPiece);
    }

    @Override
    public void primalityTest(StdRequest request, StreamObserver<PrimalityTestResponse> responseObserver) {
//            System.out.println("receive primalityTest RPC");
        if (id == 1 && !worker.primalityTestWaiting) {
            boolean passPrimalityTest = worker.primalityTestHost();
            PrimalityTestResponse response;
            if (passPrimalityTest) {
                response = RpcUtility.Response.newPrimalityTestResponse(1);
            } else {
                response = RpcUtility.Response.newPrimalityTestResponse(0);
            }
            responseObserver.onNext(response);
        } else {
            BigInteger result = worker.primalityTestGuest(new BigInteger(request.getContents().toByteArray()));
            responseObserver.onNext(RpcUtility.Response.newPrimalityTestResponse(id, result));
        }
        responseObserver.onCompleted();
    }

    @Override
    public void exchangeGamma(StdRequest request, StreamObserver<StdResponse> responseObserver) {
        int id = request.getId();
        BigInteger gamma = new BigInteger(request.getContents().toByteArray());
        responseObserver.onNext(RpcUtility.Response.newStdResponse(id));
        responseObserver.onCompleted();
        worker.getDataReceiver().receiveGamma(id, gamma);
    }

    @Override
    public void exchangeGammaSum(StdRequest request, StreamObserver<StdResponse> responseObserver) {
        int id = request.getId();
        BigInteger gammaSum = new BigInteger(request.getContents().toByteArray());
        responseObserver.onNext(RpcUtility.Response.newStdResponse(id));
        responseObserver.onCompleted();
        worker.getDataReceiver().receiveGammaSum(id, gammaSum);
    }

    @Override
    public void generatePrivateKey(StdRequest request, StreamObserver<StdResponse> responseObserver) {
        worker.generatePrivateKey();
        responseObserver.onNext(RpcUtility.Response.newStdResponse(id));
        responseObserver.onCompleted();
    }

    @Override
    public void decrypt(StdRequest request, StreamObserver<StdResponse> responseObserver) {
        String encryptedString = new String(request.getContents().toByteArray());
        String shadow = RSA.localDecrypt(encryptedString, worker.getKey());
        responseObserver.onNext(RpcUtility.Response.newStdResponse(id, shadow));
        responseObserver.onCompleted();
    }
}
