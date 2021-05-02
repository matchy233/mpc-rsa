public class Main {
    /**
    * ToDo:
    * Implement the main loop of the program.
    * Basic structures:
    *   determine manager/worker role
    *       if worker:
    *           wait for manager call to form cluster
    *       if manager:
    *           input all worker's address & port
    *           form cluster
     *              inform all workers their ids and address/ports of other workers
    *   manager inform workers to generate key pairs
    *   manager determine P as a [key bit length] bit length prime ans send to workers
     *      l = (k-1)/2
     *      worker generate 2 prime numbers p, q
     *      worker generate 3 polynomials f, g, h with degree l, l, 2l
     *          f, g, h each factor smaller than P
     *          f(0) = p, g(0) = q, h(0) = 0
     *          for j = 1, j <= k, j++
     *              p_j = f(j), q_j = g(j), h_j = h(j)
     *              {p_j, q_j, h_j} is sent to worker j
     *          {p, q, h} from other workers(and itself) are stored in p_arr, q_arr, h_arr
     *              p_arr, q_arr, h_arr have size of k
     *          n = (p_arr.sum() * q_arr.sum() + h_arr.sum())%P
     *          n is broadcast to all other workers
     *          n from other workers(and itself) are stored in n_arr
     *              a (2l degree polynomial)%P can be formed such alpha(x) = n_arr[x-1]
     *              somehow find N = alpha(0)
     *                  ToDo: implement this "somehow"
    *   Do primality test on N
     *      simple Fermat test:
     *          manager pick random int g < N and broadcast to all workers
     *          worker 1 computes v = (g.power(N-p-q+1))%N
     *          worker 2 to k compute v = g.power(p+q)%N and send to worker 1
     *              worker 1 save all received v in v_arr (note v of itself not included)
     *          worker 1 verify v = (v_arr.product())%N
     *      (optional) full Boneh-Franklin test on results passing simple Fermat test
    *   Generate public and private keys
     *      worker 1 compute phi = N-p-q+1
     *      worker 2 to k compute phi = -p-q
     *          all workers generate k random numbers in ro_arr, such ro_arr.sum()%e = phi
     *          every element in ro_arr ro_i is sent to worker i
     *          all worker has a new ro_arr
     *          ro_arr.sum() is broadcast to all workers
     *      l = ro_arr_sum_arr.sum()%e
     *      zeta = l.power(-1)%e
     *      each worker compute d = (-zeta*phi)/e
     *      ToDo: finish after here
     *
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
