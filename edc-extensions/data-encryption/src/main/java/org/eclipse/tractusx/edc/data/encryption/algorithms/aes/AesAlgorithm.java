/*
 * Copyright (c) 2023 ZF Friedrichshafen AG
 * Copyright (c) 2022 Mercedes-Benz Tech Innovation GmbH
 * Copyright (c) 2021,2022 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.eclipse.tractusx.edc.data.encryption.algorithms.aes;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.eclipse.tractusx.edc.data.encryption.algorithms.CryptoAlgorithm;
import org.eclipse.tractusx.edc.data.encryption.data.CryptoDataFactory;
import org.eclipse.tractusx.edc.data.encryption.data.DecryptedData;
import org.eclipse.tractusx.edc.data.encryption.data.EncryptedData;
import org.eclipse.tractusx.edc.data.encryption.key.AesKey;
import org.eclipse.tractusx.edc.data.encryption.util.ArrayUtil;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Objects;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class AesAlgorithm implements CryptoAlgorithm<AesKey> {

    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final String AES = "AES";
    private static final Object MONITOR = new Object();

    private final SecureRandom secureRandom;

    private final CryptoDataFactory cryptoDataFactory;
    private AesInitializationVectorIterator initializationVectorIterator;

    public AesAlgorithm(CryptoDataFactory cryptoDataFactory) {
        this.cryptoDataFactory = Objects.requireNonNull(cryptoDataFactory);

        // We use new SecureRandom() and not SecureRandom.getInstanceStrong(), as the second one
        // would use a blocking algorithm, which leads to an increased encryption time of up to 3
        // minutes. Since we have already used /dev/urandom, which only provides pseudo-randomness and
        // is also non-blocking, switching to a non-blocking algorithm should not matter here either.
        this.secureRandom = new SecureRandom();
        this.initializationVectorIterator = new AesInitializationVectorIterator(this.secureRandom);
    }

    @Override
    public synchronized EncryptedData encrypt(DecryptedData data, AesKey key)
            throws IllegalBlockSizeException, BadPaddingException, InvalidKeyException,
            NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException {

        final byte[] initializationVector;
        synchronized (MONITOR) {
            if (!initializationVectorIterator.hasNext()) {
                initializationVectorIterator = new AesInitializationVectorIterator(this.secureRandom);
            }

            initializationVector = initializationVectorIterator.next();
        }

        Cipher cipher = Cipher.getInstance(AES_GCM, new BouncyCastleProvider());
        final SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(), AES);
        final GCMParameterSpec gcmParameterSpec =
                new GCMParameterSpec(16 * 8 /* =128 */, initializationVector);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmParameterSpec);
        byte[] encrypted = cipher.doFinal(data.getBytes());
        byte[] encryptedWithVector = ArrayUtil.concat(initializationVector, encrypted);

        return cryptoDataFactory.encryptedFromBytes(encryptedWithVector);
    }

    @Override
    public DecryptedData decrypt(EncryptedData data, AesKey key)
            throws IllegalBlockSizeException, BadPaddingException, InvalidKeyException,
            NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException {
        byte[] encryptedWithVector = data.getBytes();
        byte[] initializationVector = ArrayUtil.subArray(encryptedWithVector, 0, 16);
        byte[] encrypted = ArrayUtil.subArray(encryptedWithVector, 16, encryptedWithVector.length - 16);

        Cipher cipher = Cipher.getInstance(AES_GCM, new BouncyCastleProvider());
        final SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(), AES);
        final GCMParameterSpec gcmParameterSpec =
                new GCMParameterSpec(16 * 8 /* =128 */, initializationVector);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmParameterSpec);
        byte[] decryptedData = cipher.doFinal(encrypted);
        return cryptoDataFactory.decryptedFromBytes(decryptedData);
    }

    public String getAlgorithm() {
        return this.secureRandom.getAlgorithm();
    }
}
