package mpc.project.Worker;

import io.grpc.Channel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import mpc.project.*;
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
    public void shutDownWorker(StdRequest request, StreamObserver<StdResponse> responseObserver){
        String shutDownMessage = new String((request.getContents().toByteArray()));
        System.out.println("Shutting down worker by shutDown RPC request, message: " + shutDownMessage);
        System.exit(0);
    }

    @Override
    public void initializeBPiece(StdRequest request, StreamObserver<StdResponse> responseObserver) {
        int id = request.getId();
        BigInteger b = new BigInteger(request.getContents().toByteArray());
        long workflowID = request.getWorkflowID();
        worker.getDataReceiver().receiveBPiece(id, b, workflowID);
        responseObserver.onNext(RpcUtility.Response.newStdResponse(id));
        responseObserver.onCompleted();
    }

    @Override
    public void hostModulusGeneration(StdRequest request, StreamObserver<StdResponse> responseObserver){
        // id is used for bitNum now, not id
        int bitNum = request.getId();
        BigInteger randomPrime = new BigInteger(request.getContents().toByteArray());
        long workflowID = request.getWorkflowID();
        BigInteger modulus = worker.hostModulusGeneration(bitNum, randomPrime, workflowID);
        responseObserver.onNext(RpcUtility.Response.newStdResponse(id, modulus));
        responseObserver.onCompleted();
    }

    @Override
    public void generateModulusPiece(StdRequest request, StreamObserver<StdResponse> responseObserver) {
        // id is used for bitNum now, not id
        int bitNum = request.getId();
        BigInteger randomPrime = new BigInteger(request.getContents().toByteArray());
        long workflowID = request.getWorkflowID();
        BigInteger modulus = worker.generateModulus(bitNum, randomPrime, workflowID);
        responseObserver.onNext(RpcUtility.Response.newStdResponse(id, modulus));
        responseObserver.onCompleted();
    }

    @Override
    public void exchangePrimesPQH(ExchangePrimespqhRequest request, StreamObserver<StdResponse> responseObserver) {
        int id = request.getId();
        BigInteger p = new BigInteger(request.getP().toByteArray());
        BigInteger q = new BigInteger(request.getQ().toByteArray());
        BigInteger h = new BigInteger(request.getH().toByteArray());
        long workflowID = request.getWorkflowID();
        worker.getDataReceiver().receivePHQ(id, p, q, h, workflowID);
        responseObserver.onNext(RpcUtility.Response.newStdResponse(id));
        responseObserver.onCompleted();
    }

    @Override
    public void exchangeNPiece(StdRequest request, StreamObserver<StdResponse> responseObserver) {
        int id = request.getId();
        BigInteger nPiece = new BigInteger(request.getContents().toByteArray());
        long workflowID = request.getWorkflowID();
        worker.getDataReceiver().receiveNPiece(id, nPiece, workflowID);
        responseObserver.onNext(RpcUtility.Response.newStdResponse(id));
        responseObserver.onCompleted();
    }

    @Override
    public void hostPrimalityTest(StdRequest request, StreamObserver<StdResponse> responseObserver){
        long workflowID = request.getWorkflowID();
        boolean passPrimalityTest = worker.primalityTestHost(workflowID);
        int resultCode = passPrimalityTest? 1 : 0;
        responseObserver.onNext(RpcUtility.Response.newStdResponse(resultCode));
        responseObserver.onCompleted();
    }

    @Override
    public void primalityTest(StdRequest request, StreamObserver<PrimalityTestResponse> responseObserver) {
        int hostID = request.getId();
        BigInteger g = new BigInteger(request.getContents().toByteArray());
        long workflowID = request.getWorkflowID();
        BigInteger result = worker.primalityTestGuest(hostID, g, workflowID);
        responseObserver.onNext(RpcUtility.Response.newPrimalityTestResponse(id, result));
        responseObserver.onCompleted();
    }

    @Override
    public void abortModulusGeneration(StdRequest request, StreamObserver<StdResponse> responseObserver){
        worker.setAbortModulusGeneration(true);
        responseObserver.onNext(RpcUtility.Response.newStdResponse(id));
        responseObserver.onCompleted();
    }

    @Override
    public void exchangeGamma(StdRequest request, StreamObserver<StdResponse> responseObserver) {
        int id = request.getId();
        BigInteger gamma = new BigInteger(request.getContents().toByteArray());
        long workflowID = request.getWorkflowID();
        worker.getDataReceiver().receiveGamma(id, gamma, workflowID);
        responseObserver.onNext(RpcUtility.Response.newStdResponse(id));
        responseObserver.onCompleted();
    }

    @Override
    public void exchangeGammaSum(StdRequest request, StreamObserver<StdResponse> responseObserver) {
        int id = request.getId();
        BigInteger gammaSum = new BigInteger(request.getContents().toByteArray());
        long workflowID = request.getWorkflowID();
        worker.getDataReceiver().receiveGammaSum(id, gammaSum, workflowID);
        responseObserver.onNext(RpcUtility.Response.newStdResponse(id));
        responseObserver.onCompleted();
    }

    @Override
    public void generatePrivateKey(StdRequest request, StreamObserver<StdResponse> responseObserver) {
        long workflowID = request.getWorkflowID();
        worker.generatePrivateKey(workflowID);
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
