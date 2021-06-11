package mpc.project.Manager;

import io.grpc.*;

import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Scanner;

import mpc.project.StdRequest;
import mpc.project.WorkerServiceGrpc;
import mpc.project.util.Key;
import mpc.project.util.Pair;
import mpc.project.util.RSA;
import mpc.project.util.RpcUtility;
import org.apache.commons.lang.time.DurationFormatUtils;

public class ManagerMain {
    final int clusterMaxSize = 48;
    final int clusterMinSize = 3;
    final int keyBitLength;
    private int clusterSize;
    private final boolean parallelGeneration;
    private Random rnd;
    private Server server;
    final private int portNum;
    private int id;
    private BigInteger randomPrime;
    private boolean interactMode;
    private boolean initializedAddressBook = false;
    final private ManagerRPCSender rpcSender = new ManagerRPCSender(this);

    public ManagerRPCSender getRpcSender() {
        return rpcSender;
    }

    final private ManagerDataReceiver dataReceiver = new ManagerDataReceiver(this);

    public ManagerDataReceiver getDataReceiver() {
        return dataReceiver;
    }

    final private Key key = new Key();

    public int getClusterSize() {
        return clusterSize;
    }

    public void dummyLogging(String log){
        if(interactMode){
            System.out.println(log);
        }
    }

    private boolean addClusterNode(String target, int workerId, List<WorkerServiceGrpc.WorkerServiceStub> stubs) {
        dummyLogging("verifying validity of " + target);
        Channel channel = ManagedChannelBuilder.forTarget(target).usePlaintext().build();
        WorkerServiceGrpc.WorkerServiceBlockingStub testStub = WorkerServiceGrpc.newBlockingStub(channel);
        StdRequest formClusterRequest = RpcUtility.Request.newStdRequest(workerId, randomPrime);
        try {
            testStub.formCluster(formClusterRequest);
        } catch (StatusRuntimeException e) {
            dummyLogging("Failed to add into cluster: " + e.getMessage());
            return false;
        }
        stubs.add(WorkerServiceGrpc.newStub(channel));
        dummyLogging(target + " is registered successfully");
        return true;
    }

    private String[] formClusterInteractive() {
        Scanner input = new Scanner(System.in);
        System.out.println("please enter the address:port of all workers");
        System.out.println("one in each line, \"end\" marks the end");
        List<WorkerServiceGrpc.WorkerServiceStub> stubs = new ArrayList<>();
        ArrayList<String> addressBook = new ArrayList<>();
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
            if (!addClusterNode(inLine, i, stubs)) {
                i--;
            }else{
                addressBook.add(inLine);
            }
        }
        clusterSize = stubs.size();
        WorkerServiceGrpc.WorkerServiceStub[] stubsArr = new WorkerServiceGrpc.WorkerServiceStub[stubs.size()];
        stubs.toArray(stubsArr);
        rpcSender.setStubs(stubsArr);
        return addressBook.toArray(new String[0]);
    }

    private void formCluster(String[] workerTargets){
        List<WorkerServiceGrpc.WorkerServiceStub> stubs = new ArrayList<>();
        int id = 1;
        for(String workerTarget : workerTargets){
            if(addClusterNode(workerTarget, id, stubs)){
                id++;
            }
        }
        clusterSize = stubs.size();
        WorkerServiceGrpc.WorkerServiceStub[] stubsArr = new WorkerServiceGrpc.WorkerServiceStub[stubs.size()];
        stubs.toArray(stubsArr);
        rpcSender.setStubs(stubsArr);
    }

    private boolean formNetwork(String[] addressBook) {
        StringBuilder midStringBuilder = new StringBuilder();
        for (String target : addressBook) {
            midStringBuilder.append(target).append(";");
        }
        String midString = midStringBuilder.toString();
        for (int id = 1; id <= clusterSize; id++) {
            rpcSender.sendFormNetworkRequest(id, midString);
        }
        dataReceiver.waitNetworkForming();
        dummyLogging("Network formation successful");
        return true;
    }

    public ManagerMain(int portNum, int keyBitLength, boolean parallelGeneration,
                       boolean interactMode, String[] addressBook) {
        this.portNum = portNum;
        this.rnd = new Random();
        this.keyBitLength = keyBitLength;
        this.parallelGeneration = parallelGeneration;
        this.interactMode = interactMode;
        // Fixme: hard codding 3 * keyBitLength might be a bad idea for large bit length, maybe need to look into this
        this.randomPrime = BigInteger.probablePrime(3 * keyBitLength / 2, rnd);
        if(addressBook != null && addressBook.length>0){
            initializedAddressBook = true;
            formCluster(addressBook);
            formNetwork(addressBook);
            generatePrivateKey();
        }
//        try {
//            this.server = ServerBuilder.forPort(portNum)
//                    .addService(new ManagerRPCReceiverService())
//                    .build().start();
//        } catch (Exception e) {
//            System.out.println(e.getMessage());
//            System.exit(-2);
//        }
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                if (server != null) {
                    rpcSender.broadcastShutDownWorkerRequest("Manager exits");
                    server.shutdownNow();
                }
            }
        });
    }

    private long validModulusGeneration() {
        dataReceiver.resetModulusGenerationBucket();
        Instant start = Instant.now();
        int generationHostBound = parallelGeneration ? clusterSize : 1;
        for (int id = 1; id <= generationHostBound; id++) {
            rpcSender.sendHostModulusGenerationRequest(id, keyBitLength, randomPrime, id);
        }
        Pair<BigInteger, Long> modulusWorkflowPair = dataReceiver.waitModulusGeneration();
        BigInteger resultModulus = modulusWorkflowPair.first;
        Long resultWorkflowID = modulusWorkflowPair.second;
        Instant end = Instant.now();
        long durationMillis = Duration.between(start, end).toMillis();
        String timeString = DurationFormatUtils.formatDuration(durationMillis, "HH:mm:ss.SSS");
        dummyLogging("generation time consumption: " + timeString);
        for (int id = 1; id <= clusterSize; id++) {
            rpcSender.sendAbortModulusGenerationRequest(id);
        }
        key.setN(resultModulus);
        dummyLogging(key.toPKCS1PublicString());
        return resultWorkflowID;
    }

    private void generatePrivateKey() {
        long workflowID = validModulusGeneration();
        for (int id = 1; id <= clusterSize; id++) {
            rpcSender.sendGeneratePrivateKeyRequest(id, workflowID);
        }
        dataReceiver.waitPrivateKeyGeneration();
    }

    public String[] decrypt(String s) {
        String[] decryptionShadows = new String[clusterSize];
        for (int id = 1; id <= clusterSize; id++) {
            rpcSender.sendDecryptionRequest(id, s);
        }
        dataReceiver.waitDecryptionShadow(decryptionShadows);
        return decryptionShadows;
    }

    private void interact(){
        Scanner scanner = new Scanner(System.in);
        while(true){
            System.out.println("\nEnter command (type \"help\" for help): ");
            String string = scanner.nextLine();
            String[] args = string.split(" ");
            switch (args[0]) {
                case "help":{
                    String helpMsg = "Supported operations: \n" +
                            "help, regenerate, loop_generate, print_key, trial_decrypt, decrypt, quit\n\n" +
                            "help: show this help message\n" +
                            "regenerate: regenerate the key pairs\n" +
                            "loop_generate: generate key pairs by n times, mainly for performance testing\n" +
                            "print_key: print the current public key\n" +
                            "trial_decrypt: enter a string, encrypt by the current public key and decrypt it to show" +
                                    "the correctness\n" +
                            "decrypt: enter a string encrypted by the public key and decrypt it\n" +
                            "quit: quit application and shut down the cluster";
                    System.out.println(helpMsg);
                    break;
                }
                case "regenerate":{
                    generatePrivateKey();
                    break;
                }
                case "loop_generate":{
                    try {
                        int loopTurns = Integer.parseInt(args[1]);
                        for (int i = 0; i < loopTurns; i++) {
                            generatePrivateKey();
                        }
                    } catch (NumberFormatException e) {
                        System.out.println("not valid loop turns");
                    } catch (ArrayIndexOutOfBoundsException e) {
                        System.out.println("usage: " + args[0] + " {loop turns}");
                    }
                    break;
                }
                case "print_key":{
                    System.out.println(this.key.toPKCS1PublicString());
                    break;
                }
                case "trial_decrypt": {
                    System.out.println("Enter the string you want to do trial decryption with, end with new line");
                    String message = scanner.nextLine();
                    String encryptedMessage = RSA.encrypt(message, key);
                    System.out.println("Encrypted String: " + encryptedMessage);
                    String[] distributedDecryptionResults = decrypt(encryptedMessage);
                    if(distributedDecryptionResults == null){
                        continue;
                    }
                    String decryptedMessage = RSA.combineDecryptionResult(distributedDecryptionResults, key);
                    System.out.println("Decrypted string: " + decryptedMessage);
                    break;
                }
                case "decrypt": {
                    System.out.println("Enter the string encrypted by my public key:");
                    String encryptedMessage = scanner.nextLine();
                    String[] distributedDecryptionResults = decrypt(encryptedMessage);
                    if(distributedDecryptionResults == null){
                        continue;
                    }
                    String decryptedMessage = RSA.combineDecryptionResult(distributedDecryptionResults, key);
                    System.out.println("Decrypted string: " + decryptedMessage);
                    break;
                }
                case "quit":{
                    System.out.println("Good bye");
                    System.exit(0);
                }
                default:{
                    System.out.println("Unsupported operation");
                    break;
                }
            }
        }
    }

    public void runInteractive() {
        interactMode = true;
        if(!initializedAddressBook){
            String[] addressBook = formClusterInteractive();
            formNetwork(addressBook);
            generatePrivateKey();
        }
        interact();
    }
}
