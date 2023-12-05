package mqtt;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.KeyGenerator;
import java.security.SecureRandom;

public class AesCbcCryptDecrypt {

    private static final String characterEncoding = "UTF-8";
    private static final String cipherTransformation = "AES/CBC/PKCS5Padding";
    private static final String aesEncryptionAlgorithm = "AES";

    public AesCbcCryptDecrypt() {

    }

    private static String convertToHexString(byte[] data) {
        StringBuffer buf = new StringBuffer();
        for (int i = 0; i < data.length; i++) {
            int halfbyte = (data[i] >>> 4) & 0x0F;
            int two_halfs = 0;
            do {
                if ((0 <= halfbyte) && (halfbyte <= 9))
                    buf.append((char) ('0' + halfbyte));
                else
                    buf.append((char) ('a' + (halfbyte - 10)));
                halfbyte = data[i] & 0x0F;
            } while (two_halfs++ < 1);
        }
        return buf.toString();
    }

    public Map<String, Object> generateAesKeyIv() throws Exception {
        // --- Generate a random AES key
        KeyGenerator keyGen = KeyGenerator.getInstance(aesEncryptionAlgorithm);
        keyGen.init(256); // You can use 192 or 256 as well
        SecretKey keySecred = keyGen.generateKey();
        // --- Generate a random IV
        byte[] ivBytes = new byte[32];
        SecureRandom random = new SecureRandom();
        random.nextBytes(ivBytes);
        // --- get key formats
        byte[] keyBytes = keySecred.getEncoded();
        String keyHex = convertToHexString(keyBytes);
        String keyBase64 = Base64.getEncoder().encodeToString(keySecred.getEncoded());
        // --- get iv formats
        // byte[] ivBytes = new byte[16];
        String ivHex = convertToHexString(ivBytes);
        String ivBase64 = Base64.getEncoder().encodeToString(ivBytes);
        // -- put to object
        Map<String, Object> keyIv = new HashMap<String, Object>();
        keyIv.put("keyBase64", keyBase64);
        keyIv.put("ivBase64", ivBase64);
        keyIv.put("keyHex", keyHex);
        keyIv.put("ivHex", ivHex);
        keyIv.put("keyText", new String(keyBytes, characterEncoding));
        keyIv.put("ivText", new String(ivBytes, characterEncoding));
        // --- return
        return keyIv;
    }

    public String encrypt(String unencryptedText, String keyBase64, String ivBase64)
            throws Exception {
        // --- get key and iv
        byte[] keyBytes = Base64.getDecoder().decode(keyBase64.getBytes());
        byte[] ivBytes = Base64.getDecoder().decode(ivBase64.getBytes());
        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, aesEncryptionAlgorithm);
        IvParameterSpec ivSpec = new IvParameterSpec(ivBytes);
        // --- setup cypher
        Cipher cipher = Cipher.getInstance(cipherTransformation);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
        cipher.getAlgorithm();
        // --- encrypt
        byte[] plainTextBytes = unencryptedText.getBytes();
        byte[] encryptedBytes = cipher.doFinal(plainTextBytes);

        String encryptedTextBase64 = Base64.getEncoder().encodeToString(encryptedBytes);
        // String encryptedHex = javax.xml.bind.DatatypeConverter.printHexBinary(encryptedBytes);
        // String encryptedText = new String(encryptedBytes, characterEncoding);
        // --- return encoded base64
        return encryptedTextBase64;
    }

    public String decrypt(String textEncryptedBase64, String keyBase64, String ivBase64)
            throws Exception {
        // --- get / set key and iv
        byte[] keyBytes = Base64.getDecoder().decode(keyBase64.getBytes());
        byte[] ivBytes = Base64.getDecoder().decode(ivBase64.getBytes());
        SecretKeySpec secretKeySpecy = new SecretKeySpec(keyBytes, aesEncryptionAlgorithm);
        IvParameterSpec ivParameterSpec = new IvParameterSpec(ivBytes);
        // -- setup cypher
        Cipher cipher = Cipher.getInstance(cipherTransformation);
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpecy, ivParameterSpec);
        // -- decrypt
        byte[] textEncryptedByte = Base64.getDecoder().decode(textEncryptedBase64.getBytes());
        byte[] textUnencryptedByte = cipher.doFinal(textEncryptedByte);
        String textUnencryptedAscii = new String(textUnencryptedByte, characterEncoding);
        // --- return string
        return textUnencryptedAscii;
    }
}