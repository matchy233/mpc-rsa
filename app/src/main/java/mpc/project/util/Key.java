package mpc.project.util;

import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.DERGenerator;
import org.bouncycastle.asn1.DERSequenceGenerator;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.math.BigInteger;

public class Key {
    private BigInteger N = null;
    private BigInteger d = null;
    private BigInteger e;

    public Key() {
        this.e = BigInteger.valueOf(65537);
//        this.d = BigInteger.valueOf(0);
//        this.N = BigInteger.valueOf(0);
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

    /**
     * Output the public key in PEM PKCS#1 (X.509) format
     *
     * @return : the formatted public key
     */
    public String toPKCS1PublicString() throws IOException {
        ASN1Integer asn1N = new ASN1Integer(N);
        ASN1Integer asn1E = new ASN1Integer(e);
        ByteArrayOutputStream derOutputStream = new ByteArrayOutputStream();
        DERSequenceGenerator derSequenceGenerator = new DERSequenceGenerator(derOutputStream);
        derSequenceGenerator.addObject(asn1N);
        derSequenceGenerator.addObject(asn1E);
        derSequenceGenerator.close();
        byte[] content = derOutputStream.toByteArray();
        PemObject pemObject = new PemObject("RSA PUBLIC KEY", content);
        StringWriter stringWriter = new StringWriter();
        PemWriter pemWriter = new PemWriter(stringWriter);
        try {
            pemWriter.writeObject(pemObject);
            pemWriter.close();
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
        return stringWriter.toString();
    }

}
