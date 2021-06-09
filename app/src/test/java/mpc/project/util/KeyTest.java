package mpc.project.util;

import org.junit.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.security.*;

import static org.junit.Assert.*;

public class KeyTest {

    private BigInteger getCoprime(BigInteger m, SecureRandom random) {
        int length = m.bitLength() - 1;
        BigInteger e = BigInteger.probablePrime(length, random);
        while (!(m.gcd(e)).equals(BigInteger.ONE)) {
            e = BigInteger.probablePrime(length, random);
        }
        return e;
    }

    @Test
    public void testRSA(){
        int clusterSize = 5;
        int keySize = 512;
        SecureRandom random = new SecureRandom();
        // Choose two distinct prime numbers p and q.
        BigInteger p = BigInteger.probablePrime(keySize / 2, random);
        BigInteger q = BigInteger.probablePrime(keySize / 2, random);
        // Compute n = pq (modulus)
        BigInteger modulus = p.multiply(q);
        // Compute φ(n) = φ(p)φ(q) = (p − 1)(q − 1) = n - (p + q -1), where φ is Euler's totient function.
        // and choose an integer e such that 1 < e < φ(n) and gcd(e, φ(n)) = 1; i.e., e and φ(n) are coprime.
        BigInteger m = (p.subtract(BigInteger.ONE)).multiply(q.subtract(BigInteger.ONE));
        BigInteger publicExponent = getCoprime(m, random);
        // Determine d as d ≡ e−1 (mod φ(n)); i.e., d is the multiplicative inverse of e (modulo φ(n)).
        BigInteger privateExponent = publicExponent.modInverse(m);

        BigInteger[] privateExponentArr = MathUtility.generateRandomArraySumToN(clusterSize, privateExponent, random);
//        BigInteger[] privateExponentArr = new BigInteger[] {privateExponent};
        Key publicKey = new Key();
        publicKey.setE(publicExponent);
        publicKey.setN(modulus);
        Key keys[] = new Key[clusterSize];
        for(int i = 0; i < keys.length; i++){
            keys[i] = new Key();
            keys[i].setN(modulus);
            keys[i].setE(publicExponent);
            keys[i].setD(privateExponentArr[i]);
        }
        String testMessage = "Lorem ipsum dolor sit amet, consectetur adipiscing elit.\n"
                .concat(" Vestibulum in mauris et nisi iaculis pulvinar non vehicula purus.\n")
                .concat("Nulla facilisi. Curabitur venenatis, nunc eu fermentum porta, urna erat volutpat nulla,\n")
                .concat(" eu pellentesque massa sem sit amet velit. Mauris fringilla vulputate diam,\n")
                .concat(" in pretium lectus sodales ac. Integer ipsum dolor, molestie eu mi sollicitudin,\n")
                .concat(" lacinia tincidunt massa. Maecenas vitae aliquam dolor.\n")
                .concat(" Donec at urna viverra arcu convallis malesuada eleifend sed turpis.\n");

        String encryptedMessage = RSA.encrypt(testMessage, publicKey);

        String[] msgs = new String[clusterSize];
        for(int i = 0; i < msgs.length; i++){
            msgs[i] = RSA.localDecrypt(encryptedMessage, keys[i]);
        }

        assertEquals(testMessage, RSA.combineDecryptionResult(msgs, publicKey));
    }

    @Test
    public void testToPKCS1PublicString() {
        Key key = new Key();
        key.setN(BigInteger.valueOf(57761551163L));
        try {
            System.out.println(key.toPKCS1PublicString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}