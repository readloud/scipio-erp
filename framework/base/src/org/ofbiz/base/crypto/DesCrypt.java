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

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESedeKeySpec;
import javax.crypto.spec.IvParameterSpec;

import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.GeneralException;

/**
 * Utility class for doing DESded (3DES) Two-Way Encryption
 * <p>
 * SCIPIO: WARNING: 2018-04: DES is no longer considered secure
 * and should not be used as main cipher in any code; some code may
 * still remain where it is used as pure fallback only, to read back old records.
 */
public class DesCrypt {

    private static final Debug.OfbizLogger module = Debug.getOfbizLogger(java.lang.invoke.MethodHandles.lookup().lookupClass());

    /**
     * Generate a DES key.
     * @deprecated SCIPIO: 2018-04: DES is no longer considered secure for encryption
     * and this class should only be used to decrypt old records, for backward-compatibility
     * reasons.
     */
    public static Key generateKey() throws NoSuchAlgorithmException {
        KeyGenerator keyGen = KeyGenerator.getInstance("DESede");

        Debug.logWarning("WARNING: A call to DES generateKey() was made"
                + "; DES encryption is insecure and should not be used to encrypt any new data", module);

        // generate the DES3 key
        return keyGen.generateKey();
    }

    private static volatile int encryptWarnCount = 0; // SCIPIO 
    
    /**
     * Encrypt bytes with DES.
     * @deprecated SCIPIO: 2018-04: DES is no longer considered secure for encryption
     * and this class should only be used to decrypt old records, for backward-compatibility
     * reasons.
     */
    @Deprecated
    public static byte[] encrypt(Key key, byte[] bytes) throws GeneralException {

        if (encryptWarnCount < 10) { // SCIPIO: Show this the first ~10 times, then stop, because may flood log
            Debug.logWarning("WARNING: DES encrypt() called"
                + "; DES encryption is insecure and should not be used to encrypt any new data", module);
            encryptWarnCount++; // NOTE: no need to synchronize; approx. is fine
        }

        Cipher cipher = DesCrypt.getCipher(key, Cipher.ENCRYPT_MODE);
        byte[] encBytes = null;
        try {
            encBytes = cipher.doFinal(bytes);
        } catch (IllegalStateException | IllegalBlockSizeException | BadPaddingException e) {
            throw new GeneralException(e);
        }
        return encBytes;
    }

    public static byte[] decrypt(Key key, byte[] bytes) throws GeneralException {
        Cipher cipher = DesCrypt.getCipher(key, Cipher.DECRYPT_MODE);
        byte[] decBytes = null;
        try {
            decBytes = cipher.doFinal(bytes);
        } catch (IllegalStateException | IllegalBlockSizeException | BadPaddingException e) {
            throw new GeneralException(e);
        }
        return decBytes;
    }

    public static Key getDesKey(byte[] rawKey) throws GeneralException {
        SecretKeyFactory skf = null;
        try {
            skf = SecretKeyFactory.getInstance("DESede");
        } catch (NoSuchAlgorithmException e) {
            throw new GeneralException(e);
        }

        // load the raw key
        if (rawKey.length > 0) {
            DESedeKeySpec desedeSpec1 = null;
            try {
                desedeSpec1 = new DESedeKeySpec(rawKey);
            } catch (InvalidKeyException e) {
                throw new GeneralException(e);
            }

            // create the SecretKey Object
            Key key = null;
            try {
                key = skf.generateSecret(desedeSpec1);
            } catch (InvalidKeySpecException e) {
                throw new GeneralException(e);
            }
            return key;
        }
        throw new GeneralException("Not a valid DESede key!");
    }

    // return a cipher for a key - DESede/CBC/PKCS5Padding IV = 0
    protected static Cipher getCipher(Key key, int mode) throws GeneralException {
        byte[] zeros = { 0, 0, 0, 0, 0, 0, 0, 0 };
        IvParameterSpec iv = new IvParameterSpec(zeros);

        // create the Cipher - DESede/CBC/NoPadding
        Cipher encCipher = null;
        try {
            encCipher = Cipher.getInstance("DESede/CBC/PKCS5Padding");
        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            throw new GeneralException(e);
        }
        try {
            encCipher.init(mode, key, iv);
        } catch (InvalidKeyException | InvalidAlgorithmParameterException e) {
            throw new GeneralException(e);
        }
        return encCipher;
    }
}
