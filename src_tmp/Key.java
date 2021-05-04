import java.math.BigInteger;

public class Key {
    /**
     * ToDo:
     * implement key generation methods
     */
    /* For Test */
    public BigInteger N;
    public BigInteger d;
    public BigInteger e;

    /* For Test */
    public Key(int N, int d, int e) {
        this.N = BigInteger.valueOf(N);
        this.d = BigInteger.valueOf(d);
        this.e = BigInteger.valueOf(e);
    }

    public static Key[] generateKeyPair(){
        /*
        * Return the generated public key and private key
        * result[0] is the public key and result[1] is the private key
        * */
        Key[] result = {null, null};
        return result;
    }
    public static Key combine(Key[] inputKeys){
        /*
        * Combine N (public) keys into one
        * */
        return null;
    }
    public String toString(){
        return "";
    }
    public Integer toInteger(){
        return 0;
    }
}
