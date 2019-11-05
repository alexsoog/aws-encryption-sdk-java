/*
 * Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file except
 * in compliance with the License. A copy of the License is located at
 *
 * http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.amazonaws.encryptionsdk.internal;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.SecureRandom;
import java.util.Map;

/**
 * A JceKeyCipher based on the Advanced Encryption Standard in Galois/Counter Mode.
 */
class AesGcmJceKeyCipher extends JceKeyCipher {
    private static final int NONCE_LENGTH = 12;
    private static final int TAG_LENGTH = 128;
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private final SecureRandom rnd = new SecureRandom();

    AesGcmJceKeyCipher(SecretKey key) {
        super(key, key);
    }

    private static byte[] specToBytes(final GCMParameterSpec spec) {
        final byte[] nonce = spec.getIV();
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (final DataOutputStream dos = new DataOutputStream(baos)) {
            dos.writeInt(spec.getTLen());
            dos.writeInt(nonce.length);
            dos.write(nonce);
            dos.close();
            baos.close();
        } catch (final IOException ex) {
            throw new AssertionError("Impossible exception", ex);
        }
        return baos.toByteArray();
    }

    private static GCMParameterSpec bytesToSpec(final byte[] data, final int offset) {
        final ByteArrayInputStream bais = new ByteArrayInputStream(data, offset, data.length - offset);
        try (final DataInputStream dis = new DataInputStream(bais)) {
            final int tagLen = dis.readInt();
            final int nonceLen = dis.readInt();
            final byte[] nonce = new byte[nonceLen];
            dis.readFully(nonce);
            return new GCMParameterSpec(tagLen, nonce);
        } catch (final IOException ex) {
            throw new AssertionError("Impossible exception", ex);
        }
    }

    @Override
    WrappingData buildWrappingCipher(final Key key, final Map<String, String> encryptionContext)
            throws GeneralSecurityException {
        final byte[] nonce = new byte[NONCE_LENGTH];
        rnd.nextBytes(nonce);
        final GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH, nonce);
        final Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, key, spec);
        final byte[] aad = EncryptionContextSerializer.serialize(encryptionContext);
        cipher.updateAAD(aad);
        return new WrappingData(cipher, specToBytes(spec));
    }

    @Override
    Cipher buildUnwrappingCipher(final Key key, final byte[] extraInfo, final int offset,
                                 final Map<String, String> encryptionContext) throws GeneralSecurityException {
        final GCMParameterSpec spec = bytesToSpec(extraInfo, offset);
        final Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, key, spec);
        final byte[] aad = EncryptionContextSerializer.serialize(encryptionContext);
        cipher.updateAAD(aad);
        return cipher;
    }
}