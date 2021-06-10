
package mpc.project;

import mpc.project.Manager.ManagerMain;
import mpc.project.Worker.WorkerMain;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Scanner;

public class App {
    private static class UnsupportedArgException extends Exception {
        public String content;

        public UnsupportedArgException(String content) {
            this.content = content;
        }
    }

    private static void printHelpMsg() {
        // Todo: write a real help message function
        String helpMsg = "";
        helpMsg += "this is a dummy help msg";
        System.out.println(helpMsg);
    }

    private static boolean isServer(String[] args) throws UnsupportedArgException {
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("-mode")) {
                if (args[i + 1].equals("manager")) {
                    return true;
                } else if (args[i + 1].equals("worker")) {
                    return false;
                } else {
                    throw new UnsupportedArgException("unsupported mode!");
                }
            }
        }
        return false;
    }

    static boolean managerMode = false;
    static int portNum = 5083;
    static int keyBitLength = 1024;
    static boolean managerParallelGeneration = false;
    static String[] addressBook = null;

    private static void parseArguments(String[] args) throws UnsupportedArgException {
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--mode":
                case "-m":
                    if (args[i + 1].equals("manager")) {
                        managerMode = true;
                    } else if (args[i + 1].equals("worker")) {
                        managerMode = false;
                    } else {
                        throw new UnsupportedArgException("unsupported mode");
                    }
                    break;
                case "--port":
                case "-p":
                    try {
                        portNum = Integer.parseInt(args[i + 1]);
                    } catch (NumberFormatException e) {
                        throw new UnsupportedArgException("unsupported port number");
                    }
                    break;
                case "--keyBitLength":
                case "-k":
                    try {
                        keyBitLength = Integer.parseInt(args[i + 1]);
                    } catch (NumberFormatException e) {
                        throw new UnsupportedArgException("unsupported bit length number");
                    }
                    break;
                case "--parallel":
                case "-P":
                    try {
                        managerParallelGeneration = !(Integer.parseInt(args[i + 1]) == 0);
                    } catch (NumberFormatException e) {
                        throw new UnsupportedArgException("unsupported parallel flag");
                    }
                    break;
                case "--clusterConfig":
                case "-c": {
                    String path = args[i+1];
                    File file = new File(path);
                    try {
                        Scanner fileScanner = new Scanner(file);
                        ArrayList<String> addressBookList = new ArrayList<>();
                        while(fileScanner.hasNextLine()){
                            addressBookList.add(fileScanner.nextLine().trim());
                        }
                        addressBook = addressBookList.toArray(new String[0]);
                    } catch (FileNotFoundException e) {
                        throw new UnsupportedArgException("cannot load cluster config file");
                    }
                }
            }
        }
    }

    public static void main(String[] args) {
        if (args.length <= 0) {
            printHelpMsg();
            return;
        }
        try {
            parseArguments(args);
        } catch (UnsupportedArgException e) {
            System.out.println("Unsupported Argument: " + e.content);
            return;
        }
        if (managerMode) {
            ManagerMain managerMain = new ManagerMain(
                    portNum, keyBitLength, managerParallelGeneration, true, addressBook);
            managerMain.runInteractive();
        } else {
            WorkerMain workerMain = new WorkerMain(portNum);
            workerMain.run();
        }
    }
}
