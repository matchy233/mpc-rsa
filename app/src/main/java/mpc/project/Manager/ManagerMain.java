package mpc.project.Manager;

import io.grpc.*;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import mpc.project.StdRequest;
import mpc.project.WorkerServiceGrpc;
import mpc.project.util.Key;
import mpc.project.util.Pair;
import mpc.project.util.RSA;
import mpc.project.util.RpcUtility;

public class ManagerMain {
    final int clusterMaxSize = 48;
    final int clusterMinSize = 3;
    final int keyBitLength = 512;
    private int clusterSize;
    private Random rnd;
    private Server server;
    final private int portNum;
    private int id;
    private BigInteger randomPrime;
    private ArrayList<WorkerServiceGrpc.WorkerServiceStub> stubs;
    private ArrayList<String> addressBook;
    private String selfAddress;
    final private ManagerRPCSender rpcSender = new ManagerRPCSender(this);

    public ManagerRPCSender getRpcSender() {
        return rpcSender;
    }

    final private ManagerDataReceiver dataReceiver = new ManagerDataReceiver(this);

    public ManagerDataReceiver getDataReceiver() {
        return dataReceiver;
    }

    private Key key = new Key();

    public int getClusterSize() {
        return clusterSize;
    }

    private boolean addClusterNode(String target, int workerId) {
        System.out.println("verifying validity of " + target);
        Channel channel = ManagedChannelBuilder.forTarget(target).usePlaintext().build();
        WorkerServiceGrpc.WorkerServiceBlockingStub testStub = WorkerServiceGrpc.newBlockingStub(channel);
        StdRequest formClusterRequest = RpcUtility.Request.newStdRequest(workerId, randomPrime);
        StdRequest registerManagerRequest = RpcUtility.Request.newStdRequest(workerId, selfAddress);
        try {
            testStub.registerManager(registerManagerRequest);
            testStub.formCluster(formClusterRequest);
        } catch (StatusRuntimeException e) {
            System.out.println("Failed to add into cluster: " + e.getMessage());
            return false;
        }
        stubs.add(WorkerServiceGrpc.newStub(channel));
        addressBook.add(target);
        System.out.println(target + " is registered successfully");
        return true;
    }

    private void formCluster() {
        Scanner input = new Scanner(System.in);
        System.out.println("please enter the address you want workers to use to connect to you");
        // will not check validity, be careful
        selfAddress = input.nextLine();
        System.out.println("please enter the address:port of all workers");
        System.out.println("one in each line, \"end\" marks the end");
        stubs = new ArrayList<WorkerServiceGrpc.WorkerServiceStub>();
        addressBook = new ArrayList<String>();
        for (int i = 1; i <= clusterMaxSize; i++) {
            String inLine = input.nextLine();
            if (inLine.equals("end")) {
                if (stubs.size() >= clusterMinSize) {
                    break;
                } else {
                    System.out.println("Too few workers to form a cluster");
                    System.out.println("Current number of workers: " + stubs.size());
                    System.out.println("Minimum number of workers: " + clusterMinSize);
                    i--;
                    continue;
                }
            }
            if (!addClusterNode(inLine, i)) {
                i--;
            }
        }
        clusterSize = addressBook.size();
        WorkerServiceGrpc.WorkerServiceStub[] stubsArr = new WorkerServiceGrpc.WorkerServiceStub[stubs.size()];
        stubs.toArray(stubsArr);
        rpcSender.setStubs(stubsArr);
    }

    private boolean formNetwork() {
        StringBuilder midStringBuilder = new StringBuilder();
        for (String target : addressBook) {
            midStringBuilder.append(target).append(";");
        }
        String midString = midStringBuilder.toString();
        for (int id = 1; id <= clusterSize; id++) {
            rpcSender.sendFormNetworkRequest(id, midString);
        }
        dataReceiver.waitNetworkForming();
        System.out.println("Network formation successfully");
        return true;
    }

    public ManagerMain(int portNum) {
        this.portNum = portNum;
        this.rnd = new Random();
        this.randomPrime = BigInteger.probablePrime(3 * keyBitLength, rnd);
        try {
            this.server = ServerBuilder.forPort(portNum)
                    .addService(new ManagerRPCReceiverService())
                    .build().start();
            System.out.println("Manager server started");
        } catch (Exception e) {
            System.out.println(e.getMessage());
            System.exit(-2);
        }
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                System.out.println("Shutting down RPC server");
                if (server != null) {
                    rpcSender.broadcastShutDownWorkerRequest("Manager exits");
                    server.shutdownNow();
                }
            }
        });
    }

//    private final AtomicInteger workflowCounter = new AtomicInteger(0);
//    private BigInteger resultModulus;
//    private long resultWorkflowID;
//    class ModulusGenerationThread extends Thread{
//        private final int workerID;
//        private final Semaphore resultLock;
//        private final long workflowID;
//        private boolean stop = false;
//        public ModulusGenerationThread(int workerID, Semaphore resultLock, long workflowID){
//            this.workerID = workerID;
//            this.resultLock = resultLock;
//            this.workflowID = workflowID;
//        }
//        @Override
//        public void run(){
//            boolean isValidModulus;
//            BigInteger modulus;
//            do{
//                // generate a possible modulus
//                rpcSender.sendHostModulusGenerationRequest(workerID, keyBitLength, randomPrime, workflowID);
//                modulus = dataReceiver.waitModulusGeneration(workflowID);
//
//                // perform primality test
//                rpcSender.sendHostPrimalityTestRequest(workerID, workflowID);
//                isValidModulus = dataReceiver.waitPrimalityTestResult(workflowID);
//
//                // stop check
//                if(stop){
//                    return;
//                }
//
//            }while (!isValidModulus);
//            resultWorkflowID = workflowID;
//            resultModulus = modulus;
//            resultLock.release();
//        }
//        public void setStop(){
//            stop = true;
//        }
//    }

    private long validModulusGeneration(){
        for(int id = 1; id <= clusterSize; id++){
            rpcSender.sendHostModulusGenerationRequest(id, keyBitLength, randomPrime, id);
        }
        Pair<BigInteger, Long> modulusWorkflowPair = dataReceiver.waitModulusGeneration();
        BigInteger resultModulus = modulusWorkflowPair.first;
        Long resultWorkflowID = modulusWorkflowPair.second;
        System.out.println(
                "finished modulus generation, modulus: "+resultModulus+", workflow id: "+resultWorkflowID
        );
        for(int id = 1; id <= clusterSize; id++){
            rpcSender.sendAbortModulusGenerationRequest(id);
        }
        return resultWorkflowID;
    }

    private void generatePrivateKey() {
        for (int id = 1; id <= clusterSize; id++) {
            rpcSender.sendGeneratePrivateKeyRequest(id);
        }
        dataReceiver.waitPrivateKeyGeneration();
    }

    public String[] decrypt(String s) {
        String[] decryptionShadows = new String[clusterSize];
        for (int id = 1; id <= clusterSize; id++) {
            rpcSender.sendDecryptionRequest(id, s, decryptionShadows);
        }
        dataReceiver.waitDecryptionShadow();
        return decryptionShadows;
    }

    public void run() {
        formCluster();
        formNetwork();
        long workflowID = validModulusGeneration();
        //generatePrivateKey();
        Scanner scanner = new Scanner(System.in);
        while (!scanner.nextLine().equals("quit")) {
            String s = scanner.nextLine();
            String encryptedString = RSA.encrypt(s, key);
            System.out.println("Encrypted String: " + encryptedString);
            String[] distributedDecryptionResults = decrypt(encryptedString);
            String decryptedString = RSA.combineDecryptionResult(distributedDecryptionResults, key);
            System.out.println("Decrypted string: " + decryptedString);
            System.out.println(
                    "Decryption " +
                            (decryptedString.equals(s) ? "successes!" : "fails!"));
        }
        System.exit(0);
    }
}
