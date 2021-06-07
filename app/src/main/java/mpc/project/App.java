
package mpc.project;

import mpc.project.Manager.ManagerMain;
import mpc.project.Worker.WorkerMain;

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

    private static void parseArguments(String[] args) throws UnsupportedArgException {
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--mode") || args[i].equals("-m")) {
                if (args[i + 1].equals("manager")) {
                    managerMode = true;
                } else if (args[i + 1].equals("worker")) {
                    managerMode = false;
                } else {
                    throw new UnsupportedArgException("unsupported mode");
                }
            } else if (args[i].equals("--port") || args[i].equals("-p")) {
                try {
                    portNum = Integer.parseInt(args[i + 1]);
                } catch (NumberFormatException e) {
                    throw new UnsupportedArgException("unsupported port number");
                }
            } else if (args[i].equals("--keyBitLength") || args[i].equals("-k")) {
                try {
                    keyBitLength = Integer.parseInt(args[i + 1]);
                } catch (NumberFormatException e) {
                    throw new UnsupportedArgException("unsupported port number");
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
            ManagerMain managerMain = new ManagerMain(portNum, keyBitLength);
            managerMain.run();
        } else {
            WorkerMain workerMain = new WorkerMain(portNum);
            workerMain.run();
        }
    }
}
