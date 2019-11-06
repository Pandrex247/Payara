package fish.payara.nucleus.hazelcast.encryption;

import com.hazelcast.core.HazelcastException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import java.nio.ByteBuffer;
import java.security.AlgorithmParameters;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidParameterSpecException;
import java.util.Base64;

/**
 * Class that encodes and decodes symmetric keys.
 *
 * @author Andrew Pielage <andrew.pielage@payara.fish>
 * @author Rudy De Busscher <rudy.de.busscher@payara.fish>
 */
public class SymmetricEncryptor {
    private static final String AES_ALGORITHM = "AES/GCM/NoPadding";

    public static String encode(byte[] value, SecretKey secretKey) {
        // Generate IV.
        byte[] saltBytes = new byte[20];
        new SecureRandom().nextBytes(saltBytes);

        try {
            // Encrypting the data
            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            AlgorithmParameters params = cipher.getParameters();
            byte[] ivBytes = params.getParameterSpec(IvParameterSpec.class).getIV();
            byte[] encryptedTextBytes = cipher.doFinal(value);

            // Prepend salt and IV
            byte[] buffer = new byte[saltBytes.length + ivBytes.length + encryptedTextBytes.length];
            System.arraycopy(saltBytes, 0, buffer, 0, saltBytes.length);
            System.arraycopy(ivBytes, 0, buffer, saltBytes.length, ivBytes.length);
            System.arraycopy(encryptedTextBytes, 0, buffer, saltBytes.length + ivBytes.length,
                    encryptedTextBytes.length);
            return Base64.getEncoder().encodeToString(buffer);
        } catch (NoSuchAlgorithmException | InvalidKeyException | NoSuchPaddingException | IllegalBlockSizeException
                | InvalidParameterSpecException | BadPaddingException exception) {
            throw new HazelcastException(exception);
        }
    }

    public static byte[] decode(String encryptedText, SecretKey secretKey) {

        byte[] decryptedTextBytes;
        try {
            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            // Strip off the salt and IV
            ByteBuffer buffer = ByteBuffer.wrap(Base64.getDecoder().decode((encryptedText)));
            byte[] saltBytes = new byte[20];
            buffer.get(saltBytes, 0, saltBytes.length);
            byte[] ivBytes = new byte[cipher.getBlockSize()];
            buffer.get(ivBytes, 0, ivBytes.length);
            byte[] encryptedTextBytes = new byte[buffer.capacity() - saltBytes.length - ivBytes.length];

            buffer.get(encryptedTextBytes);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(ivBytes));
            decryptedTextBytes = cipher.doFinal(encryptedTextBytes);
        } catch (BadPaddingException exception) {
            // BadPaddingException -> Wrong key
            throw new HazelcastException("BadPaddingException caught decoding data, " +
                    "this can be caused by an incorrect key: ", exception);
        } catch (IllegalBlockSizeException | NoSuchAlgorithmException | InvalidAlgorithmParameterException
                | InvalidKeyException | NoSuchPaddingException exception) {
            throw new HazelcastException(exception);
        }

        return decryptedTextBytes;
    }
}
