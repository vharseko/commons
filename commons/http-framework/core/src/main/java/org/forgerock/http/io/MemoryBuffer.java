/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2010–2011 ApexIdentity Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */

package org.forgerock.http.io;

import java.io.IOException;
import java.util.Arrays;

/**
 * A buffer that uses a byte array for data storage. The byte array starts at a
 * prescribed initial length, and grows exponentially up to the prescribed
 * limit.
 * <p>
 * <strong>Note:</strong> This implementation is not synchronized. If multiple
 * threads access a buffer concurrently, threads that append to the buffer
 * should synchronize on the instance of this object.
 */
final class MemoryBuffer implements Buffer {

    /** The byte array storing buffer data. */
    byte[] data; // package scope to give TemporaryBuffer access without intermediate copy

    /** The current length of the buffer. */
    private final int limit;

    /** Current length of the buffer. */
    private int length = 0;

    MemoryBuffer(final int initial, final int limit) {
        data = new byte[initial];
        this.limit = limit;
    }

    @Override
    public byte read(final int pos) throws IOException {
        notClosed();
        if (pos < data.length) {
            return data[pos];
        }
        throw new IndexOutOfBoundsException();
    }

    @Override
    public int read(final int pos, final byte[] b, final int off, final int len) throws IOException {
        if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException();
        }
        notClosed();
        int n = 0;
        if (pos < length) {
            n = Math.min(len, length - pos);
            System.arraycopy(data, pos, b, off, n);
        }
        return n;
    }

    @Override
    public void append(final byte b) throws IOException {
        notClosed();
        final int end = this.length + 1;
        growBufferIfNecessary(end);
        data[this.length] = b;
        this.length = end;
    }

    @Override
    public void append(final byte[] b, final int off, final int len) throws IOException {
        if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException();
        }
        notClosed();
        final int end = this.length + len;
        growBufferIfNecessary(end);
        System.arraycopy(b, off, data, this.length, len);
        this.length = end;
    }

    private void growBufferIfNecessary(final int end) throws OverflowException {
        if (end > limit) {
            throw new OverflowException();
        }
        if (data.length < end) {
            // buffer grows exponentially (up to limit)
            data = Arrays.copyOf(data, Math.max(end, Math.min(limit, data.length << 1)));
        }
    }

    @Override
    public int length() {
        return length;
    }

    @Override
    public void close() {
        data = null;
    }

    @Override
    public void finalize() throws Throwable {
        close();
        super.finalize();
    }

    /**
     * Throws an {@link IOException} if the buffer is closed.
     */
    private void notClosed() throws IOException {
        if (data == null) {
            throw new IOException("buffer is closed");
        }
    }
}
