package mpc.project.util;

import java.math.BigInteger;

public class Key {
    private BigInteger N;
    private BigInteger d;
    private BigInteger e;

    public Key() {
        this.e = BigInteger.valueOf(65537);
        this.d = BigInteger.valueOf(0);
        this.N = BigInteger.valueOf(0);
    }

    public void setD(BigInteger d) {
        this.d = d;
    }

    public void setE(BigInteger e) {
        this.e = e;
    }

    public void setN(BigInteger N) {
        this.N = N;
    }

    public BigInteger getD() {
        return d;
    }

    public BigInteger getE() {
        return e;
    }

    public BigInteger getN() {
        return N;
    }
}
