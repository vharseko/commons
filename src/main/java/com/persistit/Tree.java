/**
 * Copyright © 2005-2012 Akiban Technologies, Inc.  All rights reserved.
 * 
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * This program may also be available under different license terms.
 * For more information, see www.akiban.com or contact licensing@akiban.com.
 * 
 * Contributors:
 * Akiban Technologies, Inc.
 */

package com.persistit;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import com.persistit.TimelyResource.VersionCreator;
import com.persistit.exception.CorruptVolumeException;
import com.persistit.exception.PersistitException;
import com.persistit.util.Debug;
import com.persistit.util.Util;

/**
 * <p>
 * Cached meta-data about a single B-Tree within a {@link Volume}. A
 * <code>Tree</code> object keeps track of the <code>Volume</code>, the index
 * root page, the index depth, various other statistics and the
 * {@link Accumulator}s for a B-Tree.
 * </p>
 * <p>
 * <code>Tree</code> instances are created by
 * {@link Volume#getTree(String, boolean)}. If the <code>Volume</code> already
 * has a B-Tree with the specified name, then the <code>Tree</code> object
 * returned by <code>getTree</code> reflects the stored information. Otherwise,
 * <code>getTree</code> can create a new B-Tree. In either case, the
 * <code>Tree</code> is merely a transient in-memory cache for the B-Tree
 * information ultimately stored on disk.
 * </p>
 * <p>
 * Persistit ensures that <code>Tree</code> instances are unique, that is, for a
 * given <code>Volume</code> and name, there is only one <code>Tree</code>. if
 * multiple threads call {@link Volume#getTree(String, boolean)} for the same
 * name on the same volume, the first such call will create a new
 * <code>Tree</code> instance and subsequent calls will return the same
 * instance.
 * </p>
 * <p>
 * Each <code>Tree</code> may have up to 64 {@link Accumulator} instances that
 * may be used to aggregate statistical information such as counters.
 * <code>Accumulator</code>s work within the MVCC transaction scheme to provide
 * highly concurrent access to a small number of variables that would otherwise
 * cause a significant performance degradation.
 * </p>
 */
public class Tree extends SharedResource {
    final static int MAX_SERIALIZED_SIZE = 512;
    final static int MAX_TREE_NAME_SIZE = 256;
    final static int MAX_ACCUMULATOR_COUNT = 64;

    private final String _name;
    private final Volume _volume;
    private final AtomicReference<Object> _appCache = new AtomicReference<Object>();
    private final AtomicInteger _handle = new AtomicInteger();

    private final Accumulator[] _accumulators = new Accumulator[MAX_ACCUMULATOR_COUNT];

    private final TreeStatistics _treeStatistics = new TreeStatistics();

    private final TimelyResource<Version> _timelyResource;
    
    private VersionCreator<Version> _creator = new VersionCreator<Version>() {

        @Override
        public Version createVersion() throws PersistitException {
            return new Version();
        }
        
    };

    private class Version implements PrunableResource {
        volatile long _rootPageAddr;
        volatile int _depth;
        final AtomicLong _changeCount = new AtomicLong(0);

        public boolean prune() throws PersistitException {
            _volume.getStructure().removeTree(_rootPageAddr, _depth);
            return true;
        }
    }

    Tree(final Persistit persistit, final Volume volume, final String name) {
        super(persistit);
        final int serializedLength = name.getBytes().length;
        if (serializedLength > MAX_TREE_NAME_SIZE) {
            throw new IllegalArgumentException("Tree name too long: " + name.length() + "(as " + serializedLength
                    + " bytes)");
        }
        _name = name;
        _volume = volume;
        _generation.set(1);
        _timelyResource = new TimelyResource<Version>(persistit);
    }

    private Version version() {
        try {
            return _timelyResource.getVersion(_persistit.getTransaction(), _creator);
        } catch (Exception e) {
            throw new RuntimeException(e); // TODO
        }
    }

    /**
     * @return The volume containing this <code>Tree</code>.
     */
    public Volume getVolume() {
        return _volume;
    }

    /**
     * @return This <code>Tree</code>'s name
     */
    public String getName() {
        return _name;
    }

    @Override
    public int hashCode() {
        return _volume.hashCode() ^ _name.hashCode();
    }

    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (o instanceof Tree) {
            final Tree tree = (Tree) o;
            return _name.equals(tree._name) && _volume.equals(tree.getVolume());
        } else {
            return false;
        }
    }

    /**
     * Returns the page address of the root page of this <code>Tree</code>. The
     * root page will be a data page if the <code>Tree</code> has only one page,
     * or will be the top index page of the B-Tree.
     * 
     * @return The page address
     */
    public long getRootPageAddr() {
        return version()._rootPageAddr;
    }

    /**
     * @return the number of levels of the <code>Tree</code>.
     */
    public int getDepth() {
        return version()._depth;
    }

    void changeRootPageAddr(final long rootPageAddr, final int deltaDepth) throws PersistitException {
        Debug.$assert0.t(isOwnedAsWriterByMe());
        final Version version = version();
        version._rootPageAddr = rootPageAddr;
        version._depth += deltaDepth;
    }

    void bumpChangeCount() {
        //
        // Note: the changeCount only gets written when there's a structure
        // change in the tree that causes it to be committed.
        //
        version()._changeCount.incrementAndGet();
    }

    /**
     * @return The number of key-value insert/delete operations performed on
     *         this tree; does not including replacement of an existing value
     */
    long getChangeCount() {
        return version()._changeCount.get();
    }

    /**
     * Save a Tree in the directory
     * 
     * @param value
     */
    int store(final byte[] bytes, final int index) {
        final byte[] nameBytes = Util.stringToBytes(_name);
        final Version version = version();
        Util.putLong(bytes, index, version._rootPageAddr);
        Util.putLong(bytes, index + 8, version._changeCount.get());
        Util.putShort(bytes, index + 16, version._depth);
        Util.putShort(bytes, index + 18, nameBytes.length);
        Util.putBytes(bytes, index + 20, nameBytes);
        return 20 + nameBytes.length;
    }

    /**
     * Load an existing Tree from the directory
     * 
     * @param value
     */
    int load(final byte[] bytes, final int index, final int length) {
        final int nameLength = length < 20 ? -1 : Util.getShort(bytes, index + 18);
        if (nameLength < 1 || nameLength + 20 > length) {
            throw new IllegalStateException("Invalid tree record is too short for tree " + _name + ": " + length);
        }
        final String name = new String(bytes, index + 20, nameLength);
        if (!_name.equals(name)) {
            throw new IllegalStateException("Invalid tree name recorded: " + name + " for tree " + _name);
        }
        final Version version = version();
        version._rootPageAddr = Util.getLong(bytes, index);
        version._changeCount.set(Util.getLong(bytes, index + 8));
        version._depth = Util.getShort(bytes, index + 16);
        return length;
    }

    /**
     * Initialize a Tree.
     * 
     * @param rootPageAddr
     * @throws PersistitException
     */
    void setRootPageAddress(final long rootPageAddr) throws PersistitException {
        final Version version = version();
        if (version._rootPageAddr != rootPageAddr) {
            // Derive the index depth
            Buffer buffer = null;
            try {
                buffer = getVolume().getStructure().getPool().get(_volume, rootPageAddr, false, true);
                final int type = buffer.getPageType();
                if (type < Buffer.PAGE_TYPE_DATA || type > Buffer.PAGE_TYPE_INDEX_MAX) {
                    throw new CorruptVolumeException(String.format("Tree root page %,d has invalid type %s",
                            rootPageAddr, buffer.getPageTypeName()));
                }
                version._rootPageAddr = rootPageAddr;
                version._depth = type - Buffer.PAGE_TYPE_DATA + 1;
            } finally {
                if (buffer != null) {
                    buffer.releaseTouched();
                }
            }
        }
    }

    /**
     * Invoked when this <code>Tree</code> is being deleted. This causes
     * subsequent operations by any <code>Exchange</code>s on this
     * <code>Tree</code> to fail.
     */
    void invalidate() {
        final Version version = version();
        super.clearValid();
        version._depth = -1;
        version._rootPageAddr = -1;
        _generation.set(-1);
    }

    /**
     * @return a <code>TreeStatistics</code> object containing approximate
     *         counts of records added, removed and fetched from this
     *         </code>Tree</code>
     */
    public TreeStatistics getStatistics() {
        return _treeStatistics;
    }

    /**
     * @return a displayable description of the <code>Tree</code>, including its
     *         name, its internal tree index, its root page address, and its
     *         depth.
     */
    @Override
    public String toString() {
        final Version version = version();
        return "<Tree " + _name + " rootPageAddr=" + version._rootPageAddr + " depth=" + version._depth + " status="
                + getStatusDisplayString() + ">";
    }

    /**
     * Store an Object with this Tree for the convenience of an application.
     * 
     * @param appCache
     *            the object to be cached for application convenience.
     */
    public void setAppCache(final Object appCache) {
        _appCache.set(appCache);
    }

    /**
     * @return the object cached for application convenience
     */
    public Object getAppCache() {
        return _appCache.get();
    }

    /**
     * @return The handle value used to identify this <code>Tree</code> in the
     *         journal
     */
    public int getHandle() {
        return _handle.get();
    }

    /**
     * Assign and set the tree handle. The tree must may not be a member of a
     * temporary volume.
     * 
     * @throws PersistitException
     */
    void loadHandle() throws PersistitException {
        assert !_volume.isTemporary() : "Handle allocation for temporary tree " + this;
        _persistit.getJournalManager().handleForTree(this);
    }

    /**
     * Return an <code>Accumulator</code> for this Tree. The caller provides the
     * type (SUM, MAX, MIN or SEQ) of accumulator, and an index value between 0
     * and 63, inclusive. If the <code>Tree</code> does not yet have an
     * <code>Accumulator</code> with the specified index, this method creates
     * one of the the specified type. Otherwise the specified type must match
     * the type of the one previously.
     * 
     * @param type
     *            Type of <code>Accumulator</code>
     * @param index
     *            Application-controlled value between 0 and 63, inclusive.
     * @throws IllegalStateException
     *             if the supplied type does not match that of a previously
     *             created <code>Accumulator</code>
     */
    public synchronized Accumulator getAccumulator(final Accumulator.Type type, final int index)
            throws PersistitException {
        if (index < 0 || index >= MAX_ACCUMULATOR_COUNT) {
            throw new IllegalArgumentException("Invalid accumulator index: " + index);
        }
        Accumulator accumulator = _accumulators[index];
        if (accumulator == null) {
            final AccumulatorState saved = Accumulator.getAccumulatorState(this, index);
            long savedValue = 0;
            if (saved != null) {
                if (!saved.getTreeName().equals(getName())) {
                    throw new IllegalStateException("AccumulatorState has wrong tree name: " + saved);
                }
                if (!saved.getType().equals(type)) {
                    throw new IllegalStateException("AccumulatorState has different type: " + saved);
                }
                savedValue = saved.getValue();
            }
            accumulator = Accumulator.accumulator(type, this, index, savedValue, _persistit.getTransactionIndex());
            _accumulators[index] = accumulator;
            _persistit.addAccumulator(accumulator);
        } else if (accumulator.getType() != type) {
            throw new IllegalStateException("Wrong type " + accumulator + " is not a " + type + " accumulator");
        }
        return accumulator;
    }

    /**
     * Set the handle used to identify this Tree in the journal. May be invoked
     * only once.
     * 
     * @param handle
     * @return
     * @throws IllegalStateException
     *             if the handle has already been set
     */
    int setHandle(final int handle) {
        if (!_handle.compareAndSet(0, handle)) {
            throw new IllegalStateException("Tree handle already set");
        }
        return handle;
    }

    /**
     * Reset the handle to zero. Intended for use only by tests.
     */
    void resetHandle() {
        _handle.set(0);
    }

    /**
     * Forget about any instantiated accumulator and remove it from the active
     * list in Persistit. This should only be called in the during the process
     * of removing a tree.
     */
    void discardAccumulators() {
        for (int i = 0; i < _accumulators.length; ++i) {
            if (_accumulators[i] != null) {
                _persistit.removeAccumulator(_accumulators[i]);
                _accumulators[i] = null;
            }
        }
    }
}
