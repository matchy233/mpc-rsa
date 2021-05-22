package mpc.project;

import io.grpc.*;
import io.grpc.stub.StreamObserver;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Random;
import java.util.Scanner;

import mpc.project.util.Key;
import mpc.project.util.RSA;
import mpc.project.util.RpcUtility;

public class ManagerMain {
    final int clusterMaxSize = 48;
    final int clusterMinSize = 3;
    final int keyBitLength = 16;
    private int clusterSize;
    private Random rnd;
    private Server server;
    private int portNum;
    private int id;
    private BigInteger randomPrime;
    private ArrayList<WorkerServiceGrpc.WorkerServiceStub> stubs;
    private ArrayList<String> addressBook;
    private String selfAddress;

    private Key key = new Key();

    class ManagerServiceImpl extends ManagerServiceGrpc.ManagerServiceImplBase {
        @Override
        public void greeting(StdRequest request, StreamObserver<StdResponse> responseObserver) {
            int id = request.getId();
            System.out.println("receive greeting from worker " + id);
            StdResponse response = RpcUtility.Response.newStdResponse(id);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
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
        for (int i = 1; i < clusterMaxSize; i++) {
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
    }

    Integer formNetworkCounter = 0;
    final Object formNetworkLock = new Object();
    boolean formNetworkCounterWaiting;

    private boolean formNetwork() {
        StringBuilder midStringBuilder = new StringBuilder();
        for (String target : addressBook) {
            midStringBuilder.append(target).append(";");
        }
        String midString = midStringBuilder.toString();
        synchronized (formNetworkLock) {
            formNetworkCounterWaiting = true;
            formNetworkCounter = 0;
            for (int i = 0; i < clusterSize; i++) {
                StdRequest request = RpcUtility.Request.newStdRequest(i + 1, midString);
                stubs.get(i).formNetwork(request, new StreamObserver<StdResponse>() {
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
                        if (formNetworkCounterWaiting) {
                            synchronized (formNetworkLock) {
                                formNetworkCounter++;
                                if (formNetworkCounter == clusterSize) {
                                    formNetworkLock.notify();
                                }
                            }
                        }
                    }
                });
            }
            System.out.println("Waiting for network forming to complete");
            try {
                formNetworkLock.wait();

            } catch (InterruptedException e) {
                System.out.println("Waiting interrupted: " + e.getMessage());
                System.exit(-3);
            }
            formNetworkCounterWaiting = false;
        }
        System.out.println("Network formation successfully");
        return true;
    }

    public ManagerMain(int portNum) {
        this.portNum = portNum;
        this.rnd = new Random();
        this.randomPrime = BigInteger.probablePrime(3 * keyBitLength, rnd);
        try {
            this.server = ServerBuilder.forPort(portNum)
                    .addService(new ManagerServiceImpl())
                    .build().start();
            System.out.println("Manager server started");
        } catch (Exception e) {
            System.out.println(e.getMessage());
            System.exit(-2);
        }
    }

    final Object generateModulusLock = new Object();
    int generateModulusCounter = 0;
    boolean generateModulusWaiting = false;

    private void generateModulus() {
        synchronized (generateModulusLock) {
            generateModulusWaiting = true;
            generateModulusCounter = 0;
            for (WorkerServiceGrpc.WorkerServiceStub worker : stubs) {
                StdRequest request = RpcUtility.Request.newStdRequest(keyBitLength, randomPrime);
                worker.generateModulusPiece(request, new StreamObserver<StdResponse>() {
                    @Override
                    public void onNext(StdResponse response) {
//                        System.out.println("received by " + response.getId());
                    }

                    @Override
                    public void onError(Throwable t) {
                        System.out.println("RPC error: " + t.getMessage());
                        System.exit(-1);
                    }

                    @Override
                    public void onCompleted() {
                        if (generateModulusWaiting) {
                            synchronized (generateModulusLock) {
                                generateModulusCounter++;
                                if (generateModulusCounter == clusterSize) {
                                    generateModulusLock.notify();
                                }
                            }
                        }
                    }
                });
            }
            System.out.println("Waiting for key generation complete");
            try {
                generateModulusLock.wait();
            } catch (InterruptedException e) {
                System.out.println("Waiting interrupted: " + e.getMessage());
                System.exit(-3);
            }
            generateModulusWaiting = false;
        }
    }

    final Object primalityTestLock = new Object();
    boolean passPrimalityTest = false;
    boolean primalityTestWaiting = false;

    private void primalityTest() {
        synchronized (primalityTestLock) {
            primalityTestWaiting = true;
            passPrimalityTest = false;
            StdRequest request = RpcUtility.Request.newStdRequest(keyBitLength);
            stubs.get(0).primalityTest(request, new StreamObserver<PrimalityTestResponse>() {
                @Override
                public void onNext(PrimalityTestResponse response) {
                    System.out.println("received by " + response.getId());
                    if (primalityTestWaiting) {
                        boolean primalityTestResult;
                        if (response.getId() == 1) {
                            primalityTestResult = true;
                        } else if (response.getId() <= 0) {
                            primalityTestResult = false;
                        } else {
                            return;
                        }
                        synchronized (primalityTestLock) {
                            passPrimalityTest = primalityTestResult;
                            primalityTestLock.notify();
                        }
                    }
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
            System.out.println("Waiting for primality test to complete");
            try {
                primalityTestLock.wait();
            } catch (InterruptedException e) {
                System.out.println("Waiting interrupted: " + e.getMessage());
                System.exit(-3);
            }
            primalityTestWaiting = false;
        }
    }

    final Object generatePrivateKeyLock = new Object();
    int generatePrivateKeyCounter = 0;

    private void generatePrivateKey() {
        synchronized (generatePrivateKeyLock) {
            generatePrivateKeyCounter = 0;
            for (WorkerServiceGrpc.WorkerServiceStub worker : stubs) {
                worker.generatePrivateKey(RpcUtility.Request.newStdRequest(id), new StreamObserver<StdResponse>() {
                    @Override
                    public void onNext(StdResponse response) {
                        synchronized (generatePrivateKeyLock) {
                            generatePrivateKeyCounter++;
                            if (generatePrivateKeyCounter == clusterSize) {
                                generatePrivateKeyLock.notify();
                            }
                        }
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
            try {
                generatePrivateKeyLock.wait();
            } catch (Exception e) {
                System.out.println("Waiting interrupted: " + e.getMessage());
                System.exit(-3);
            }
        }
    }

    final Object decryptionLock = new Object();
    boolean decryptionWaiting = false;
    int decryptionCounter = 0;

    public String[] decrypt(String s) {
        String[] result = new String[clusterSize];
        synchronized (decryptionLock) {
            decryptionWaiting = true;
            decryptionCounter = 0;
            for (int i = 0; i < stubs.size(); i++) {
                stubs.get(i).decrypt(RpcUtility.Request.newStdRequest(id, s),
                        new StreamObserver<StdResponse>() {
                            @Override
                            public void onNext(StdResponse response) {
                                int j = response.getId() - 1;
                                result[j] = new String(response.getContents().toByteArray());
                            }

                            @Override
                            public void onError(Throwable t) {
                                System.out.println("decryption error: " + t.getMessage());
                            }

                            @Override
                            public void onCompleted() {
                                synchronized (decryptionLock) {
                                    decryptionCounter++;
                                    if (decryptionCounter == clusterSize) {
                                        decryptionLock.notify();
                                    }
                                }
                            }
                        });
            }
            try {
                decryptionLock.wait();
            } catch (InterruptedException e) {
                System.out.println("Waiting interrupted: " + e.getMessage());
                System.exit(-4);
            }
            decryptionWaiting = false;
        }
        return result;
    }

    public void run() {
        formCluster();
        formNetwork();
        do {
            generateModulus();
            primalityTest();
        } while (!passPrimalityTest);
        generatePrivateKey();
        Scanner scanner = new Scanner(System.in);
        while (!scanner.nextLine().equals("quit")) {
            String s = scanner.nextLine();
            String encryptedString = RSA.encrypt(s, key);
            System.out.println("Encrypted String: " + encryptedString);
            String[] distributedDecryptionResults = decrypt(encryptedString);
            String decryptedString = RSA.distributedDecrypt(distributedDecryptionResults, key);
            System.out.println("Decrypted string: " + decryptedString);
            System.out.println(
                    "Decryption " +
                    (decryptedString.equals(s) ? "successes!" : "fails!"));
        }
        System.exit(0);
    }
}
