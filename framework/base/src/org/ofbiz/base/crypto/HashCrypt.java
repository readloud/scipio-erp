/*******************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *******************************************************************************/
package org.ofbiz.base.crypto;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.RandomStringUtils;
import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.GeneralRuntimeException;
import org.ofbiz.base.util.StringUtil;
import org.ofbiz.base.util.UtilIO;
import org.ofbiz.base.util.UtilProperties;
import org.ofbiz.base.util.UtilValidate;

/**
 * Utility class for doing SHA-1/PBKDF2 One-Way Hash Encryption
 *
 */
public class HashCrypt {

    private static final Debug.OfbizLogger module = Debug.getOfbizLogger(java.lang.invoke.MethodHandles.lookup().lookupClass());
    public static final String CRYPT_CHAR_SET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789./";

    private static final String PBKDF2_SHA1 ="PBKDF2-SHA1";
    private static final String PBKDF2_SHA256 ="PBKDF2-SHA256";
    private static final String PBKDF2_SHA384 ="PBKDF2-SHA384";
    private static final String PBKDF2_SHA512 ="PBKDF2-SHA512";
    private static final int PBKDF2_ITERATIONS = UtilProperties.getPropertyAsInteger("security.properties", "password.encrypt.pbkdf2.iterations", 10000);

    private static Boolean systemCharsetUtf8 = null; // SCIPIO

    public static MessageDigest getMessageDigest(String type) {
        try {
            return MessageDigest.getInstance(type);
        } catch (NoSuchAlgorithmException e) {
            throw new GeneralRuntimeException("Could not load digestor(" + type + ")", e);
        }
    }

    /**
     * comparePassword.
     * <p>
     * SCIPIO: 2018-09-13: Modified so that <code>doCompareTypePrefix</code> and <code>doCompareBare</code> cases
     * now check using platform-independent UTF-8 in String->byte[] conversion first, with compatibility fallback on system charset
     * for legacy records. New records should always be byte-encoded in explicit UTF-8 (see {@link #cryptUTF8(String, String, String)},
     * {@link #digestHash(String, String, String)}), but Scipio maintains support for checking older-type
     * password hashes created before this time.
     */
    public static boolean comparePassword(String crypted, String defaultCrypt, String password) {
        if (crypted.startsWith("{PBKDF2")) {
            return doComparePbkdf2(crypted, password);
        } else if (crypted.startsWith("{")) {
            // SCIPIO: 2018-09-13: Check with UTF-8 first, then fallback to system charset (for backward-compat)
            if (doCompareTypePrefix(crypted, defaultCrypt, password.getBytes(UtilIO.getUtf8()))) {
                return true;
            } else if (isSystemCharsetUtf8()) {
                return false;
            } else {
                // SCIPIO: Fallback on system charset - NOTE: this is the old stock code
                return doCompareTypePrefix(crypted, defaultCrypt, password.getBytes()); // SCIPIO: DEV NOTE: DO NOT ADD UTF-8 HERE!
            }
        } else if (crypted.startsWith("$")) {
            return doComparePosix(crypted, defaultCrypt, password.getBytes(UtilIO.getUtf8()));
        } else {
            // SCIPIO: 2018-09-13: Check with UTF-8 first, then fallback to system charset (for backward-compat)
            if (doCompareBare(crypted, defaultCrypt, password.getBytes(UtilIO.getUtf8()))) {
                return true;
            } else if (isSystemCharsetUtf8()) {
                return false;
            } else {
                // SCIPIO: Fallback on system charset - NOTE: this is the old stock code
                return doCompareBare(crypted, defaultCrypt, password.getBytes()); // SCIPIO: DEV NOTE: DO NOT ADD UTF-8 HERE!
            }
        }
    }

    private static boolean isSystemCharsetUtf8() { // SCIPIO: avoid rechecking this more than once/twice in beginning
        if (systemCharsetUtf8 == null) {
            systemCharsetUtf8 = UtilIO.getUtf8().equals(Charset.defaultCharset());
        }
        return systemCharsetUtf8;
    }

    private static boolean doCompareTypePrefix(String crypted, String defaultCrypt, byte[] bytes) {
        int typeEnd = crypted.indexOf("}");
        String hashType = crypted.substring(1, typeEnd);
        String hashed = crypted.substring(typeEnd + 1);
        MessageDigest messagedigest = getMessageDigest(hashType);
        messagedigest.update(bytes);
        byte[] digestBytes = messagedigest.digest();
        char[] digestChars = Hex.encodeHex(digestBytes);
        String checkCrypted = new String(digestChars);
        if (hashed.equals(checkCrypted)) {
            return true;
        }
        // This next block should be removed when all {prefix}oldFunnyHex are fixed.
        if (hashed.equals(oldFunnyHex(digestBytes))) {
            Debug.logWarning("Warning: detected oldFunnyHex password prefixed with a hashType; this is not valid, please update the value in the database with ({%s}%s)", module, hashType, checkCrypted);
            return true;
        }
        return false;
    }

    private static boolean doComparePosix(String crypted, String defaultCrypt, byte[] bytes) {
        int typeEnd = crypted.indexOf("$", 1);
        int saltEnd = crypted.indexOf("$", typeEnd + 1);
        String hashType = crypted.substring(1, typeEnd);
        String salt = crypted.substring(typeEnd + 1, saltEnd);
        String hashed = crypted.substring(saltEnd + 1);
        return hashed.equals(getCryptedBytes(hashType, salt, bytes));
    }

    private static boolean doCompareBare(String crypted, String defaultCrypt, byte[] bytes) {
        String hashType = defaultCrypt;
        String hashed = crypted;
        MessageDigest messagedigest = getMessageDigest(hashType);
        messagedigest.update(bytes);
        return hashed.equals(oldFunnyHex(messagedigest.digest()));
    }

    /*
     * @deprecated use cryptBytes(hashType, salt, password); eventually, use
     * cryptUTF8(hashType, salt, password) after all existing installs are
     * salt-based.  If the call-site of cryptPassword is just used to create a *new*
     * value, then you can switch to cryptUTF8 directly.
     */
    @Deprecated
    public static String cryptPassword(String hashType, String salt, String password) {
        if (hashType.startsWith("PBKDF2")) {
            return password != null ? pbkdf2HashCrypt(hashType, salt, password) : null;
        }
        // FIXME: should have been getBytes("UTF-8") originally
        return password != null ? cryptBytes(hashType, salt, password.getBytes()) : null; // SCIPIO: DEV NOTE: DO NOT ADD UTF-8 HERE!
    }

    public static String cryptUTF8(String hashType, String salt, String value) {
        if (hashType.startsWith("PBKDF2")) {
            return value != null ? pbkdf2HashCrypt(hashType, salt, value) : null;
        }
        return value != null ? cryptBytes(hashType, salt, value.getBytes(UtilIO.getUtf8())) : null;
    }

    /**
     * cryptValue.
     * <p>
     * SCIPIO: <strong>WARNING:</strong> 2018-09-13: This method has been altered to
     * use explicit UTF-8 (instead of old behavior to use system charset) in the passed string->byte[] conversion,
     * so that it is consistent with {@link #cryptUTF8(String, String, String)} as used
     * in <code>LoginServices</code>. A compatibility workaround for client systems that
     * did not use UTF-8 as default system charset and already had records is now integrated into the
     * {@link #comparePassword(String, String, String)} method.
     */
    public static String cryptValue(String hashType, String salt, String value) {
        if (hashType.startsWith("PBKDF2")) {
            return value != null ? pbkdf2HashCrypt(hashType, salt, value) : null;
        }
        // SCIPIO: 2018-09-13: UtilIO.getUtf8()
        //return value != null ? cryptBytes(hashType, salt, value.getBytes()) : null;
        return value != null ? cryptBytes(hashType, salt, value.getBytes(UtilIO.getUtf8())) : null;
    }

    /**
     * cryptBytes.
     * <p>
     * SCIPIO: WARN: FIXME?: This currently does not respond to PBKDF2 hashType,
     * so you must use the {@link #cryptUTF8(String, String, String)} for PBKDF2
     * support for the time being.
     */
    public static String cryptBytes(String hashType, String salt, byte[] bytes) {
        if (hashType == null) {
            hashType = "SHA";
        }
        if (salt == null) {
            salt = RandomStringUtils.random(new SecureRandom().nextInt(15) + 1, CRYPT_CHAR_SET);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("$").append(hashType).append("$").append(salt).append("$");
        sb.append(getCryptedBytes(hashType, salt, bytes));
        return sb.toString();
    }

    private static String getCryptedBytes(String hashType, String salt, byte[] bytes) {
        try {
            MessageDigest messagedigest = MessageDigest.getInstance(hashType);
            messagedigest.update(salt.getBytes(UtilIO.getUtf8()));
            messagedigest.update(bytes);
            return Base64.encodeBase64URLSafeString(messagedigest.digest()).replace('+', '.');
        } catch (NoSuchAlgorithmException e) {
            throw new GeneralRuntimeException("Error while comparing password", e);
        }
    }

    /**
     * pbkdf2HashCrypt.
     * <p>
     * SCIPIO: 2018-09-13: WARN: If you have salt already as byte array, call 
     * {@link #pbkdf2HashCrypt(String, byte[], String)} instead.
     */
    public static String pbkdf2HashCrypt(String hashType, String salt, String value){
        return pbkdf2HashCrypt(hashType, (salt != null) ? salt.getBytes(UtilIO.getUtf8()) : (byte[]) null, value);
    }

    /**
     * SCIPIO: pbkdf2HashCrypt overload that takes a byte array for the salt instead of string.
     * <p>
     * Added 2018-09-13.
     */
    public static String pbkdf2HashCrypt(String hashType, byte[] salt, String value){
        char[] chars = value.toCharArray();
        if (salt == null || salt.length == 0) {
            salt = getSalt();
        }
        try {
            PBEKeySpec spec = new PBEKeySpec(chars, salt, PBKDF2_ITERATIONS, 64 * 4);
            SecretKeyFactory skf = SecretKeyFactory.getInstance(hashType);
            byte[] hash = Base64.encodeBase64(skf.generateSecret(spec).getEncoded());
            String pbkdf2Type = null;
            switch (hashType) {
                case "PBKDF2WithHmacSHA1":
                    pbkdf2Type = PBKDF2_SHA1;
                    break;
                case "PBKDF2WithHmacSHA256":
                    pbkdf2Type = PBKDF2_SHA256;
                    break;
                case "PBKDF2WithHmacSHA384":
                    pbkdf2Type = PBKDF2_SHA384;
                    break;
                case "PBKDF2WithHmacSHA512":
                    pbkdf2Type = PBKDF2_SHA512;
                    break;
                default:
                    pbkdf2Type = PBKDF2_SHA1;
            }
            StringBuilder sb = new StringBuilder();
            sb.append("{").append(pbkdf2Type).append("}");
            sb.append(PBKDF2_ITERATIONS).append("$");
            sb.append(org.ofbiz.base.util.Base64.base64EncodeToString(salt)).append("$"); // SCIPIO: base64EncodeToString
            sb.append(new String(hash, UtilIO.getUtf8())); // SCIPIO: UtilIO.getUtf8() (cosmetic only - base64 char range)
            return sb.toString();
        } catch (InvalidKeySpecException e) {
            throw new GeneralRuntimeException("Error while creating SecretKey", e);
        } catch (NoSuchAlgorithmException e) {
            throw new GeneralRuntimeException("Error while computing SecretKeyFactory", e);
        }
    }

    public static boolean doComparePbkdf2(String crypted, String password){
        try {
            int typeEnd = crypted.indexOf("}");
            String hashType = crypted.substring(1, typeEnd);
            String[] parts = crypted.split("\\$");
            int iterations = Integer.parseInt(parts[0].substring(typeEnd+1));
            // SCIPIO: 2018-09-13: This is ridiculous
            //byte[] salt = org.ofbiz.base.util.Base64.base64Decode(parts[1]).getBytes(UtilIO.getUtf8());
            byte[] salt = org.ofbiz.base.util.Base64.base64DecodeToBytes(parts[1]);
            byte[] hash = Base64.decodeBase64(parts[2].getBytes(UtilIO.getUtf8()));

            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, hash.length * 8);
            switch (hashType.substring(hashType.indexOf("-")+1)) {
                case "SHA256":
                    hashType = "PBKDF2WithHmacSHA256";
                    break;
                case "SHA384":
                    hashType = "PBKDF2WithHmacSHA384";
                    break;
                case "SHA512":
                    hashType = "PBKDF2WithHmacSHA512";
                    break;
                default:
                    hashType = "PBKDF2WithHmacSHA1";
            }
            SecretKeyFactory skf = SecretKeyFactory.getInstance(hashType);
            byte[] testHash = skf.generateSecret(spec).getEncoded();
            int diff = hash.length ^ testHash.length;

            for (int i = 0; i < hash.length && i < testHash.length; i++) {
                diff |= hash[i] ^ testHash[i];
            }

            return diff == 0;
        } catch (NoSuchAlgorithmException e) {
            throw new GeneralRuntimeException("Error while computing SecretKeyFactory", e);
        } catch (InvalidKeySpecException e) {
            throw new GeneralRuntimeException("Error while creating SecretKey", e);
        }
    }

    /**
     * Creates a salt.
     * <p>
     * SCIPIO: 2018-09-13: Modified to return byte array to avoid intermediate string representation.
     */
    private static byte[] getSalt() {
        try {
            SecureRandom sr = SecureRandom.getInstance("SHA1PRNG");
            byte[] salt = new byte[16];
            sr.nextBytes(salt);
            // SCIPIO: 2019-09-13: This was invalid, avoid conversion
            //return Arrays.toString(salt);
            return salt;
        } catch (NoSuchAlgorithmException e) {
            throw new GeneralRuntimeException("Error while creating salt", e);
        }
    }

    /**
     * @deprecated use digestHash("SHA", null, str)
     */
    @Deprecated
    public static String getDigestHash(String str) {
        // SCIPIO: 2018-09-13: This method may still be called from UserEvents.xml,
        // so must not use hardcode hash type!
        //return digestHash("SHA", null, str);
        return digestHash(getPasswordEncryptHashType(), null, str);
    }

    /**
     * @deprecated use digestHash(hashType, null, str))
     */
    @Deprecated
    public static String getDigestHash(String str, String hashType) {
        return digestHash(hashType, null, str);
    }

    /**
     * @deprecated use digestHash(hashType, code, str);
     */
    @Deprecated
    public static String getDigestHash(String str, String code, String hashType) {
        return digestHash(hashType, code, str);
    }

    /**
     * digestHash, with input as string.
     * <p>
     * SCIPIO: <strong>WARNING:</strong> 2018-09-13: This method has been altered to
     * use explicit UTF-8 (instead of old behavior to use system charset) in the passed string->byte[] conversion,
     * so that it is consistent with {@link #cryptUTF8(String, String, String)} as used
     * in <code>LoginServices</code>. A compatibility workaround for client systems that
     * did not use UTF-8 as default system charset and already had records is now integrated into the
     * {@link #comparePassword(String, String, String)} method.
     */
    public static String digestHash(String hashType, String code, String str) {
        if (str == null) {
            return null;
        }
        byte[] codeBytes;
        try {
            if (code == null) {
                // SCIPIO: 2018-09-13: UtilIO.getUtf8()
                //codeBytes = str.getBytes();
                codeBytes = str.getBytes(UtilIO.getUtf8());
            } else {
                codeBytes = str.getBytes(code);
            }
        } catch (UnsupportedEncodingException e) {
            throw new GeneralRuntimeException("Error while computing hash of type " + hashType, e);
        }
        return digestHash(hashType, codeBytes);
    }

    public static String digestHash(String hashType, byte[] bytes) {
        try {
            MessageDigest messagedigest = MessageDigest.getInstance(hashType);
            messagedigest.update(bytes);
            byte[] digestBytes = messagedigest.digest();
            char[] digestChars = Hex.encodeHex(digestBytes);

            StringBuilder sb = new StringBuilder();
            sb.append("{").append(hashType).append("}");
            sb.append(digestChars, 0, digestChars.length);
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new GeneralRuntimeException("Error while computing hash of type " + hashType, e);
        }
    }

    public static String digestHash64(String hashType, byte[] bytes) {
        if (hashType == null) {
            hashType = "SHA";
        }
        try {
            MessageDigest messagedigest = MessageDigest.getInstance(hashType);
            messagedigest.update(bytes);
            byte[] digestBytes = messagedigest.digest();

            StringBuilder sb = new StringBuilder();
            sb.append("{").append(hashType).append("}");
            sb.append(Base64.encodeBase64URLSafeString(digestBytes).replace('+', '.'));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new GeneralRuntimeException("Error while computing hash of type " + hashType, e);
        }
    }

    /**
     * @deprecated use cryptPassword
     */
    @Deprecated
    public static String getHashTypeFromPrefix(String hashString) {
        if (UtilValidate.isEmpty(hashString) || hashString.charAt(0) != '{') {
            return null;
        }

        return hashString.substring(1, hashString.indexOf('}'));
    }

    /**
     * @deprecated use cryptPassword
     */
    @Deprecated
    public static String removeHashTypePrefix(String hashString) {
        if (UtilValidate.isEmpty(hashString) || hashString.charAt(0) != '{') {
            return hashString;
        }

        return hashString.substring(hashString.indexOf('}') + 1);
    }

    /**
     * @deprecated use digestHashOldFunnyHex(hashType, str)
     */
    @Deprecated
    public static String getDigestHashOldFunnyHexEncode(String str, String hashType) {
        return digestHashOldFunnyHex(hashType, str);
    }

    /**
     * digestHashOldFunnyHex.
     * @deprecated SCIPIO: This method should not called by anything other than
     * {@link org.ofbiz.entity.util.EntityCrypto#OldFunnyHashStorageHandler}, and
     * it encrypts using the system charset, which is not necessarily UTF-8.
     */
    @Deprecated
    public static String digestHashOldFunnyHex(String hashType, String str) {
        return digestHashOldFunnyHex(hashType, null, str);
    }

    /**
     * digestHashOldFunnyHex.
     * <p>
     * SCIPIO: <strong>WARNING:</strong> This method is intended only for
     * {@link org.ofbiz.entity.util.EntityCrypto#OldFunnyHashStorageHandler}
     * and its use anywhere else is unsupported. If code is null, the system
     * charset is used, which is not necessarily UTF-8.
     */
    public static String digestHashOldFunnyHex(String hashType, Charset code, String str) {
        if (UtilValidate.isEmpty(hashType)) {
            hashType = "SHA";
        }
        if (str == null) {
            return null;
        }
        try {
            MessageDigest messagedigest = MessageDigest.getInstance(hashType);
            // SCIPIO: 2018-09-13: use requested charset
            byte[] strBytes = (code != null) ? str.getBytes(code) : str.getBytes(); // SCIPIO: DEV NOTE: DO NOT ADD UTF-8 HERE!

            messagedigest.update(strBytes);
            return oldFunnyHex(messagedigest.digest());
        } catch (Exception e) {
            Debug.logError(e, "Error while computing hash of type " + hashType, module);
        }
        return str;
    }

    // This next block should be removed when all {prefix}oldFunnyHex are fixed.
    private static String oldFunnyHex(byte[] bytes) {
        int k = 0;
        char[] digestChars = new char[bytes.length * 2];
        for (byte b : bytes) {
            int i1 = b;

            if (i1 < 0) {
                i1 = 127 + i1 * -1;
            }
            StringUtil.encodeInt(i1, k, digestChars);
            k += 2;
        }
        return new String(digestChars);
    }

    /**
     * SCIPIO: Gets the system-configured hash type to use.
     * <p>
     * Moved here from {@link org.ofbiz.common.login.LoginServices#getHashType()} (2018-09-13).
     */
    public static String getPasswordEncryptHashType() {
        String hashType = UtilProperties.getPropertyValue("security", "password.encrypt.hash.type");

        if (UtilValidate.isEmpty(hashType)) {
            Debug.logWarning("Password encrypt hash type is not specified in security.properties, use SHA", module);
            hashType = "SHA";
        }

        return hashType;
    }
}
