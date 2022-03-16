package cpen221.mp3.fsftbuffer;

import java.util.*;

public class FSFTBuffer<T extends Bufferable> {

    /**
     * Rep Invariant
     *
     * - 'capacity' and 'timeout' are > 0
     * - 'lhm' does not contain null elements and is not null
     * - the least recently used object is removed when put is called
     *   and the buffer has 'capacity' number of elements
     * - objects are not accessible to the client after it has spent
     *   'timeout' seconds in the buffer without being touched or updated
     *
     * --------------------------------------------------
     *
     * Abstraction Function
     *
     * A FSFTBuffer represents a cache of bufferable objects that removes objects
     * that become out of date over time
     *
     * --------------------------------------------------
     *
     * Thread Safety Argument:
     *
     * This class is thread-safe because it is synchonized:
     * All method bodies are either synchronized to this object or are called within a
     * synchronized block. Additionally, 'lhm', 'capacity', and 'timeout' are private and are not exposed
     * to the client through methods, and public variables 'DSIZE' and 'DTIMEOUT' are final integers so
     * are immutable.
     *
     */

    /* the default buffer size is 32 objects */
    public static final int DSIZE = 32;

    /* the default timeout value is 3600s */
    public static final int DTIMEOUT = 3600;

    private final Map<String,BufferableWrapper<T>> lhm;
    private final int capacity;
    private final int timeout;

    /**
     * Create a buffer with a fixed capacity and a timeout value.
     * Objects in the buffer that have not been refreshed within the
     * timeout period are removed from the cache.
     * Requires: capacity > 0 & timeout > 0
     * @param capacity the number of objects the buffer can hold
     * @param timeout  the duration, in seconds, an object should
     *                 be in the buffer before it times out
     */
    public FSFTBuffer(int capacity, int timeout) {
        this.capacity = capacity;
        lhm = Collections.synchronizedMap(new LinkedHashMap<>(capacity) {
            @Override
            protected boolean removeEldestEntry (Map.Entry<String,
                    BufferableWrapper<T>> eldest)
            {
                return size() > capacity;
            }
        });
        this.timeout = timeout;
    }

    /**
     * Create a buffer with default capacity and timeout values.
     */
    public FSFTBuffer() {
        this(DSIZE, DTIMEOUT);
    }

    /**
     * Add a value to the buffer.
     * If the buffer is full then remove the least recently accessed
     * object to make room for the new object.
     * Adding an object that already exists does not update the existing object's timeout time.
     * Modifies: removes stale objects from lhm and adds t to lhm
     * @param t the object to attempt to put in the buffer
     * @return true if put successful, false otherwise
     */
    public boolean put(T t) {
        synchronized (this) {
            removeExpiredObjects();
            boolean found = false;
            for (Map.Entry<String, BufferableWrapper<T>> entry : lhm.entrySet()) {
                if (entry.getKey().equals(t.id())) {
                    found = true;
                }
            }
            if (!found) {
                lhm.put(t.id(), new BufferableWrapper<>(t));
                return true;
            }
            return false;
        }
    }

    /**
     * Obtain an object from the buffer
     * Modifies: lhm to update the obtained object's LRU status
     * @param id the identifier of the object to be retrieved
     * @return the object that matches the identifier from the
     * buffer
     * @throws NoSuchElementException if object with id doesn't exist in the
     *                                buffer
     */
    public T get(String id) throws NoSuchElementException {
        synchronized (this) {
            for (Map.Entry<String, BufferableWrapper<T>> entry : lhm.entrySet()) {
                if (entry.getKey().equals(id) && !isStale(entry.getValue())) {
                    BufferableWrapper<T> tempKey = entry.getValue();
                    lhm.remove(entry.getKey());
                    lhm.put(id, tempKey);
                    return tempKey.getT();
                }
            }
            throw new NoSuchElementException();
        }
    }

    /**
     * Update the last refresh time for the object with the provided id.
     * This method is used to mark an object as "not stale" so that its
     * timeout is delayed.
     * Modifies: refreshes the timeout time of the touched object
     * @param id the identifier of the object to "touch"
     * @return true if successful and false otherwise
     */
    public boolean touch(String id) {
        synchronized (this) {
            if (lhm.containsKey(id)) {
                if (!isStale(lhm.get(id))) {
                    T temp = lhm.get(id).getT();
                    BufferableWrapper tempWrapper = new BufferableWrapper(temp);
                    lhm.replace(id, tempWrapper);
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Update an object in the buffer.
     * This method updates an object and acts like a "touch" to
     * renew the object in the cache.
     * Modifies: refreshes the timeout time and the content of the updated object
     * @param t the object to update
     * @return true if successful and false otherwise
     */
    public boolean update(T t) {
        synchronized (this) {
            if (lhm.containsKey(t.id())) {
                if (!isStale(lhm.get(t.id()))) {
                    lhm.replace(t.id(), new BufferableWrapper<>(t));
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Remove objects that have spent timeout time in the buffer
     * without being touched or updated.
     * Modifies: removes stale objects from the buffer
     */
    private void removeExpiredObjects(){
        List<String> toRemove = new ArrayList<>();
        for (Map.Entry<String, BufferableWrapper<T>> entry : lhm.entrySet()) {
            if (isStale(entry.getValue())){
                toRemove.add(entry.getKey());
            }
        }
        for (String s : toRemove) {
            lhm.remove(s);
        }
    }

    /**
     * Determine if a BufferableWrapper is stale.
     * @param t the BufferableWrapper to test staleness on
     * @return true if BufferableWrapper is stale, false otherwise
     */
    private boolean isStale(BufferableWrapper t) {
        long time = System.currentTimeMillis();
        long wrapperTime = t.getTimeoutTime();
        return time - wrapperTime >= timeout * 1000;
    }
}
