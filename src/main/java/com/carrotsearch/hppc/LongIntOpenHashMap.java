package com.carrotsearch.hppc;

import java.io.Serializable;
import java.util.*;

import com.carrotsearch.hppc.cursors.*;
import com.carrotsearch.hppc.hash.MurmurHash3;
import com.carrotsearch.hppc.predicates.*;
import com.carrotsearch.hppc.procedures.*;

import static com.carrotsearch.hppc.Internals.*;

/**
 * A hash map of <code>long</code> to <code>int</code>, implemented using open
 * addressing with linear probing for collision resolution.
 *
 * <p>
 * The internal buffers of this implementation ({@link #keys}, {@link #values},
 * {@link #allocated}) are always allocated to the nearest size that is a power of two. When
 * the capacity exceeds the given load factor, the buffer size is doubled.
 * </p>
 *
 * <p>See {@link ObjectObjectOpenHashMap} class for API similarities and differences against Java
 * Collections.
 *
 *
 * <p><b>Important node.</b> The implementation uses power-of-two tables and linear
 * probing, which may cause poor performance (many collisions) if hash values are
 * not properly distributed. This implementation uses rehashing
 * using {@link MurmurHash3}.</p>
 *
 * @author This code is inspired by the collaboration and implementation in the <a
 *         href="http://fastutil.dsi.unimi.it/">fastutil</a> project.
 */
@javax.annotation.Generated(date = "2011-07-12T16:58:50+0200", value = "HPPC generated from: LongIntOpenHashMap.java")
public class LongIntOpenHashMap
        implements LongIntMap, Cloneable, Serializable
{
    /**
     * Default capacity.
     */
    public final static int DEFAULT_CAPACITY = 16;

    /**
     * Minimum capacity for the map.
     */
    public final static int MIN_CAPACITY = 4;

    /**
     * Default load factor.
     */
    public final static float DEFAULT_LOAD_FACTOR = 0.75f;

    /**
     * Hash-indexed array holding all keys.
     *
     * @see #values
     */
    public long [] keys;

    /**
     * Hash-indexed array holding all values associated to the keys
     * stored in {@link #keys}.
     *
     * @see #keys
     */
    public int [] values;

    /**
     * Information if an entry (slot) in the {@link #values} table is allocated
     * or empty.
     *
     * @see #assigned
     */
    public boolean [] allocated;

    /**
     * Cached number of assigned slots in {@link #allocated}.
     */
    public int assigned;

    /**
     * The load factor for this map (fraction of allocated slots
     * before the buffers must be rehashed or reallocated).
     */
    public final float loadFactor;

    /**
     * Cached capacity threshold at which we must resize the buffers.
     */
    private int resizeThreshold;

    /**
     * The most recent slot accessed in {@link #containsKey} (required for
     * {@link #lget}).
     *
     * @see #containsKey
     * @see #lget
     */
    private int lastSlot;

    /**
     * Creates a hash map with the default capacity of {@value #DEFAULT_CAPACITY},
     * load factor of {@value #DEFAULT_LOAD_FACTOR}.
     *
     * <p>See class notes about hash distribution importance.</p>
     */
    public LongIntOpenHashMap()
    {
        this(DEFAULT_CAPACITY);
    }

    /**
     * Creates a hash map with the given initial capacity, default load factor of
     * {@value #DEFAULT_LOAD_FACTOR}.
     *
     * <p>See class notes about hash distribution importance.</p>
     *
     * @param initialCapacity Initial capacity (greater than zero and automatically
     *            rounded to the next power of two).
     */
    public LongIntOpenHashMap(int initialCapacity)
    {
        this(initialCapacity, DEFAULT_LOAD_FACTOR);
    }

    /**
     * Creates a hash map with the given initial capacity,
     * load factor.
     *
     * <p>See class notes about hash distribution importance.</p>
     *
     * @param initialCapacity Initial capacity (greater than zero and automatically
     *            rounded to the next power of two).
     *
     * @param loadFactor The load factor (greater than zero and smaller than 1).
     */
    public LongIntOpenHashMap(int initialCapacity, float loadFactor)
    {
        initialCapacity = Math.max(initialCapacity, MIN_CAPACITY);

        assert initialCapacity > 0
                : "Initial capacity must be between (0, " + Integer.MAX_VALUE + "].";
        assert loadFactor > 0 && loadFactor <= 1
                : "Load factor must be between (0, 1].";

        this.loadFactor = loadFactor;
        allocateBuffers(roundCapacity(initialCapacity));
    }

    /**
     * Create a hash map from all key-value pairs of another container.
     */
    public LongIntOpenHashMap(LongIntAssociativeContainer container)
    {
        this((int)(container.size() * (1 + DEFAULT_LOAD_FACTOR)));
        putAll(container);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int put(long key, int value)
    {
        if (assigned >= resizeThreshold)
            expandAndRehash();

        final int mask = allocated.length - 1;
        int slot = rehash(key) & mask;
        while (allocated[slot])
        {
            if (((key) == (keys[slot])))
            {
                final int oldValue = values[slot];
                values[slot] = value;
                return oldValue;
            }

            slot = (slot + 1) & mask;
        }

        assigned++;
        allocated[slot] = true;
        keys[slot] = key;
        values[slot] = value;
        return ((int) 0);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final int putAll(
            LongIntAssociativeContainer container)
    {
        final int count = this.assigned;
        for (LongIntCursor c : container)
        {
            put(c.key, c.value);
        }
        return this.assigned - count;
    }

    /**
     * Puts all key/value pairs from a given iterable into this map.
     */
    @Override
    public final int putAll(
            Iterable<? extends LongIntCursor> iterable)
    {
        final int count = this.assigned;
        for (LongIntCursor c : iterable)
        {
            put(c.key, c.value);
        }
        return this.assigned - count;
    }

    /**
     * <a href="http://trove4j.sourceforge.net">Trove</a>-inspired API method. An equivalent
     * of the following code:
     * <pre>
     * if (!map.containsKey(key)) map.put(value);
     * </pre>
     *
     * @param key The key of the value to check.
     * @param value The value to put if <code>key</code> does not exist.
     * @return <code>true</code> if <code>key</code> did not exist and <code>value</code>
     * was placed in the map.
     */
    public final boolean putIfAbsent(long key, int value)
    {
        if (!containsKey(key))
        {
            put(key, value);
            return true;
        }
        return false;
    }

    /**
     * <a href="http://trove4j.sourceforge.net">Trove</a>-inspired API method. An equivalent
     * of the following code:
     * <pre>
     * if (map.containsKey(key))
     *    map.lset(map.lget() + additionValue);
     * else
     *    map.put(key, putValue);
     * </pre>
     *
     * @param key The key of the value to adjust.
     * @param putValue The value to put if <code>key</code> does not exist.
     * @param additionValue The value to add to the existing value if <code>key</code> exists.
     * @return Returns the current value associated with <code>key</code> (after changes).
     */
    public final int putOrAdd(long key, int putValue, int additionValue)
    {
        if (assigned >= resizeThreshold)
            expandAndRehash();

        final int mask = allocated.length - 1;
        int slot = rehash(key) & mask;
        while (allocated[slot])
        {
            if (((key) == (keys[slot])))
            {
                return values[slot] += additionValue;
            }
            slot = (slot + 1) & mask;
        }

        assigned++;
        allocated[slot] = true;
        keys[slot] = key;
        int v = values[slot] = putValue;

        return v;
    }


    /**
     * Expand the internal storage buffers (capacity) or rehash current
     * keys and values if there are a lot of deleted slots.
     */
    private void expandAndRehash()
    {
        final long [] oldKeys = this.keys;
        final int [] oldValues = this.values;
        final boolean [] oldStates = this.allocated;

        assert assigned >= resizeThreshold;
        allocateBuffers(nextCapacity(keys.length));

        /*
         * Rehash all assigned slots from the old hash table. Deleted
         * slots are discarded.
         */
        final int mask = allocated.length - 1;
        for (int i = 0; i < oldStates.length; i++)
        {
            if (oldStates[i])
            {
                final long key = oldKeys[i];
                final int value = oldValues[i];

                /*  */
                /*  */

                int slot = rehash(key) & mask;
                while (allocated[slot])
                {
                    if (((key) == (keys[slot])))
                    {
                        break;
                    }
                    slot = (slot + 1) & mask;
                }

                allocated[slot] = true;
                keys[slot] = key;
                values[slot] = value;
            }
        }

        /*
         * The number of assigned items does not change, the number of deleted
         * items is zero since we have resized.
         */
        lastSlot = -1;
    }

    /**
     * Allocate internal buffers for a given capacity.
     *
     * @param capacity New capacity (must be a power of two).
     */
    private void allocateBuffers(int capacity)
    {
        this.keys = new long [capacity];
        this.values = new int [capacity];
        this.allocated = new boolean [capacity];

        this.resizeThreshold = (int) (capacity * loadFactor);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int remove(long key)
    {
        final int mask = allocated.length - 1;
        int slot = rehash(key) & mask;

        while (allocated[slot])
        {
            if (((key) == (keys[slot])))
            {
                assigned--;
                int v = values[slot];
                shiftConflictingKeys(slot);
                return v;
            }
            slot = (slot + 1) & mask;
        }

        return ((int) 0);
    }

    /**
     * Shift all the slot-conflicting keys allocated to (and including) <code>slot</code>.
     */
    protected final void shiftConflictingKeys(int slotCurr)
    {
        // Copied nearly verbatim from fastutil's impl.
        final int mask = allocated.length - 1;
        int slotPrev, slotOther;
        while (true)
        {
            slotCurr = ((slotPrev = slotCurr) + 1) & mask;

            while (allocated[slotCurr])
            {
                slotOther = rehash(keys[slotCurr]) & mask;
                if (slotPrev <= slotCurr)
                {
                    // we're on the right of the original slot.
                    if (slotPrev >= slotOther || slotOther > slotCurr)
                        break;
                }
                else
                {
                    // we've wrapped around.
                    if (slotPrev >= slotOther && slotOther > slotCurr)
                        break;
                }
                slotCurr = (slotCurr + 1) & mask;
            }

            if (!allocated[slotCurr])
                break;

            // Shift key/value pair.
            keys[slotPrev] = keys[slotCurr];
            values[slotPrev] = values[slotCurr];
        }

        allocated[slotPrev] = false;

        /*  */
        /*  */
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final int removeAll(LongContainer container)
    {
        final int before = this.assigned;

        for (LongCursor cursor : container)
        {
            remove(cursor.value);
        }

        return before - this.assigned;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final int removeAll(LongPredicate predicate)
    {
        final int before = this.assigned;

        final long [] keys = this.keys;
        final boolean [] states = this.allocated;

        for (int i = 0; i < states.length;)
        {
            if (states[i])
            {
                if (predicate.apply(keys[i]))
                {
                    assigned--;
                    shiftConflictingKeys(i);
                    // Repeat the check for the same i.
                    continue;
                }
            }
            i++;
        }
        return before - this.assigned;
    }

    /**
     * {@inheritDoc}
     *
     * <p> Use the following snippet of code to check for key existence
     * first and then retrieve the value if it exists.</p>
     * <pre>
     * if (map.containsKey(key))
     *   value = map.lget();
     * </pre>
     */
    @Override
    public int get(long key)
    {
        final int mask = allocated.length - 1;
        int slot = rehash(key) & mask;
        while (allocated[slot])
        {
            if (((key) == (keys[slot])))
            {
                return values[slot];
            }

            slot = (slot + 1) & mask;
        }
        return ((int) 0);
    }

    /**
     * Returns the last value saved in a call to {@link #containsKey}.
     *
     * @see #containsKey
     */
    public int lget()
    {
        assert lastSlot >= 0 : "Call containsKey() first.";
        assert allocated[lastSlot] : "Last call to exists did not have any associated value.";

        return values[lastSlot];
    }

    /**
     * Sets the value corresponding to the key saved in the last
     * call to {@link #containsKey}, if and only if the key exists
     * in the map already.
     *
     * @see #containsKey
     * @return Returns the previous value stored under the given key.
     */
    public int lset(int key)
    {
        assert lastSlot >= 0 : "Call containsKey() first.";
        assert allocated[lastSlot] : "Last call to exists did not have any associated value.";

        final int previous = values[lastSlot];
        values[lastSlot] = key;
        return previous;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Saves the associated value for fast access using {@link #lget}
     * or {@link #lset}.</p>
     * <pre>
     * if (map.containsKey(key))
     *   value = map.lget();
     * </pre>
     * or, for example to modify the value at the given key without looking up
     * its slot twice:
     * <pre>
     * if (map.containsKey(key))
     *   map.lset(map.lget() + 1);
     * </pre>
     */
    @Override
    public boolean containsKey(long key)
    {
        final int mask = allocated.length - 1;
        int slot = rehash(key) & mask;
        while (allocated[slot])
        {
            if (((key) == (keys[slot])))
            {
                lastSlot = slot;
                return true;
            }
            slot = (slot + 1) & mask;
        }
        lastSlot = -1;
        return false;
    }

    /**
     * Round the capacity to the next allowed value.
     */
    protected int roundCapacity(int requestedCapacity)
    {
        // Maximum positive integer that is a power of two.
        if (requestedCapacity > (0x80000000 >>> 1))
            return (0x80000000 >>> 1);

        return Math.max(MIN_CAPACITY, BitUtil.nextHighestPowerOfTwo(requestedCapacity));
    }

    /**
     * Return the next possible capacity, counting from the current buffers'
     * size.
     */
    protected int nextCapacity(int current)
    {
        assert current > 0 && Long.bitCount(current) == 1
                : "Capacity must be a power of two.";
        assert ((current << 1) > 0)
                : "Maximum capacity exceeded (" + (0x80000000 >>> 1) + ").";

        if (current < MIN_CAPACITY / 2) current = MIN_CAPACITY / 2;
        return current << 1;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Does not release internal buffers.</p>
     */
    @Override
    public void clear()
    {
        assigned = 0;

        // States are always cleared.
        Arrays.fill(allocated, false);

        /*  */

        /*  */
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int size()
    {
        return assigned;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Note that an empty container may still contain many deleted keys (that occupy buffer
     * space). Adding even a single element to such a container may cause rehashing.</p>
     */
    public boolean isEmpty()
    {
        return size() == 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode()
    {
        int h = 0;
        for (LongIntCursor c : this)
        {
            h += rehash(c.key) + rehash(c.value);
        }
        return h;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj)
    {
        if (obj != null)
        {
            if (obj == this) return true;

            if (obj instanceof LongIntMap)
            {
                /*  */
                LongIntMap other = (LongIntMap) obj;
                if (other.size() == this.size())
                {
                    for (LongIntCursor c : this)
                    {
                        if (other.containsKey(c.key))
                        {
                            int v = other.get(c.key);
                            if (((c.value) == (v)))
                            {
                                continue;
                            }
                        }
                        return false;
                    }
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * An iterator implementation for {@link #iterator}.
     */
    private final class EntryIterator extends AbstractIterator<LongIntCursor>
    {
        private final LongIntCursor cursor;

        public EntryIterator()
        {
            cursor = new LongIntCursor();
            cursor.index = -1;
        }

        @Override
        protected LongIntCursor fetch()
        {
            int i = cursor.index + 1;
            final int max = keys.length;
            while (i < max && !allocated[i])
            {
                i++;
            }

            if (i == max)
                return done();

            cursor.index = i;
            cursor.key = keys[i];
            cursor.value = values[i];

            return cursor;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Iterator<LongIntCursor> iterator()
    {
        return new EntryIterator();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T extends LongIntProcedure> T forEach(T procedure)
    {
        final long [] keys = this.keys;
        final int [] values = this.values;
        final boolean [] states = this.allocated;

        for (int i = 0; i < states.length; i++)
        {
            if (states[i])
                procedure.apply(keys[i], values[i]);
        }

        return procedure;
    }

    /**
     * Returns a specialized view of the keys of this associated container.
     * The view additionally implements {@link ObjectLookupContainer}.
     */
    public KeysContainer keys()
    {
        return new KeysContainer();
    }

    /**
     * A view of the keys inside this hash map.
     */
    public final class KeysContainer
            extends AbstractLongCollection implements LongLookupContainer
    {
        private final LongIntOpenHashMap owner =
                LongIntOpenHashMap.this;

        @Override
        public boolean contains(long e)
        {
            return containsKey(e);
        }

        @Override
        public <T extends LongProcedure> T forEach(T procedure)
        {
            final long [] localKeys = owner.keys;
            final boolean [] localStates = owner.allocated;

            for (int i = 0; i < localStates.length; i++)
            {
                if (localStates[i])
                    procedure.apply(localKeys[i]);
            }

            return procedure;
        }

        @Override
        public <T extends LongPredicate> T forEach(T predicate)
        {
            final long [] localKeys = owner.keys;
            final boolean [] localStates = owner.allocated;

            for (int i = 0; i < localStates.length; i++)
            {
                if (localStates[i])
                {
                    if (!predicate.apply(localKeys[i]))
                        break;
                }
            }

            return predicate;
        }

        @Override
        public boolean isEmpty()
        {
            return owner.isEmpty();
        }

        @Override
        public Iterator<LongCursor> iterator()
        {
            return new KeysIterator();
        }

        @Override
        public int size()
        {
            return owner.size();
        }

        @Override
        public void clear()
        {
            owner.clear();
        }

        @Override
        public int removeAll(LongPredicate predicate)
        {
            return owner.removeAll(predicate);
        }

        @Override
        public int removeAllOccurrences(final long e)
        {
            final boolean hasKey = owner.containsKey(e);
            int result = 0;
            if (hasKey)
            {
                owner.remove(e);
                result = 1;
            }
            return result;
        }
    };

    /**
     * An iterator over the set of assigned keys.
     */
    private final class KeysIterator extends AbstractIterator<LongCursor>
    {
        private final LongCursor cursor;

        public KeysIterator()
        {
            cursor = new LongCursor();
            cursor.index = -1;
        }

        @Override
        protected LongCursor fetch()
        {
            int i = cursor.index + 1;
            final int max = keys.length;
            while (i < max && !allocated[i])
            {
                i++;
            }

            if (i == max)
                return done();

            cursor.index = i;
            cursor.value = keys[i];

            return cursor;
        }
    }

    /**
     * @return Returns a container with all values stored in this map.
     */
    @Override
    public IntContainer values()
    {
        return new ValuesContainer();
    }

    /**
     * A view over the set of values of this map.
     */
    private final class ValuesContainer extends AbstractIntCollection
    {
        @Override
        public int size()
        {
            return LongIntOpenHashMap.this.size();
        }

        @Override
        public boolean isEmpty()
        {
            return LongIntOpenHashMap.this.isEmpty();
        }

        @Override
        public boolean contains(int value)
        {
            // This is a linear scan over the values, but it's in the contract, so be it.
            final boolean [] allocated = LongIntOpenHashMap.this.allocated;
            final int [] values = LongIntOpenHashMap.this.values;

            for (int slot = 0; slot < allocated.length; slot++)
            {
                if (allocated[slot] && ((value) == (values[slot])))
                {
                    return true;
                }
            }
            return false;
        }

        @Override
        public <T extends IntProcedure> T forEach(T procedure)
        {
            final boolean [] allocated = LongIntOpenHashMap.this.allocated;
            final int [] values = LongIntOpenHashMap.this.values;

            for (int i = 0; i < allocated.length; i++)
            {
                if (allocated[i])
                    procedure.apply(values[i]);
            }

            return procedure;
        }

        @Override
        public <T extends IntPredicate> T forEach(T predicate)
        {
            final boolean [] allocated = LongIntOpenHashMap.this.allocated;
            final int [] values = LongIntOpenHashMap.this.values;

            for (int i = 0; i < allocated.length; i++)
            {
                if (allocated[i])
                {
                    if (!predicate.apply(values[i]))
                        break;
                }
            }

            return predicate;
        }

        @Override
        public Iterator<IntCursor> iterator()
        {
            return new ValuesIterator();
        }

        @Override
        public int removeAllOccurrences(int e)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public int removeAll(IntPredicate predicate)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void clear()
        {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * An iterator over the set of assigned values.
     */
    private final class ValuesIterator extends AbstractIterator<IntCursor>
    {
        private final IntCursor cursor;

        public ValuesIterator()
        {
            cursor = new IntCursor();
            cursor.index = -1;
        }

        @Override
        protected IntCursor fetch()
        {
            int i = cursor.index + 1;
            final int max = keys.length;
            while (i < max && !allocated[i])
            {
                i++;
            }

            if (i == max)
                return done();

            cursor.index = i;
            cursor.value = values[i];

            return cursor;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LongIntOpenHashMap clone()
    {
        try
        {
            /*  */
            LongIntOpenHashMap cloned =
                    (LongIntOpenHashMap) super.clone();

            cloned.keys = keys.clone();
            cloned.values = values.clone();
            cloned.allocated = allocated.clone();

            return cloned;
        }
        catch (CloneNotSupportedException e)
        {
            throw new RuntimeException(e);
        }
    }

    /**
     * Convert the contents of this map to a human-friendly string.
     */
    @Override
    public String toString()
    {
        final StringBuilder buffer = new StringBuilder();
        buffer.append("[");

        boolean first = true;
        for (LongIntCursor cursor : this)
        {
            if (!first) buffer.append(", ");
            buffer.append(cursor.key);
            buffer.append("=>");
            buffer.append(cursor.value);
            first = false;
        }
        buffer.append("]");
        return buffer.toString();
    }

    /**
     * Creates a hash map from two index-aligned arrays of key-value pairs.
     */
    public static  LongIntOpenHashMap from(long [] keys, int [] values)
    {
        if (keys.length != values.length)
            throw new IllegalArgumentException("Arrays of keys and values must have an identical length.");

        LongIntOpenHashMap map = new LongIntOpenHashMap();
        for (int i = 0; i < keys.length; i++)
        {
            map.put(keys[i], values[i]);
        }
        return map;
    }

    /**
     * Create a hash map from another associative container.
     */
    public static  LongIntOpenHashMap from(LongIntAssociativeContainer container)
    {
        return new LongIntOpenHashMap(container);
    }

    /**
     * Create a new hash map without providing the full generic signature (constructor
     * shortcut).
     */
    public static  LongIntOpenHashMap newInstance()
    {
        return new LongIntOpenHashMap();
    }

    /**
     * Create a new hash map without providing the full generic signature (constructor
     * shortcut).
     */
    public static  LongIntOpenHashMap newInstance(int initialCapacity, float loadFactor)
    {
        return new LongIntOpenHashMap(initialCapacity, loadFactor);
    }
}
