public class Main {
    /**
    * ToDo:
    * Implement the main loop of the program.
    * Basic structures:
    *   determine manager/worker role
    *       if role:
    *           wait for manager call to form cluster
    *       if manager:
    *           input all worker's address & port
    *           form cluster
    *   each client generate key(piece) pairs and send public key to manager
    *   manager generate a cluster public key using keypieces
    *   manager wait for income encrypted message
    *
    *   when manager receiving message
    *       manager forward message to all workers
    *       worker generate shadows and send back to manager
    *       manager combine all shadows to form the decrypted message
    */
    public static void main(){

    }
}
