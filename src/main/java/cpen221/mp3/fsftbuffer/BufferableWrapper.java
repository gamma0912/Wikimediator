package cpen221.mp3.fsftbuffer;

public class BufferableWrapper<T extends Bufferable> {

    /**
     * Rep Invariant
     *
     * TIMEOUTTIME and t are not null
     * TIMEOUTTIME is > 0
     * Potential rep exposure due to getT returning t itself!
     *
     * --------------------------------------------------
     *
     * Abstraction Function
     *
     * A BufferableWrapper represents a pair of a T object with its creation
     * time, with t being the T object and TIMEOUTTIME being the time ie was
     * created
     */


    private final long TIMEOUTTIME;
    private T t;

    /**
     * Create a BufferableWrapper with a 'T' object and creation time recorded.
     * @param t the 'T' object to store
     */
    public BufferableWrapper(T t) {
        this.t = t;
        this.TIMEOUTTIME = System.currentTimeMillis();
    }

    /**
     * Return the time the BufferableWrapper was created or recently
     * touched/updated
     * @return TIMEOUTTIME the time this BufferableWrapper was created
     * or recently touched/updated
     */
    public long getTimeoutTime() {
        return TIMEOUTTIME;
    }

    /**
     * Return t
     * @return t
     */
    public T getT() {
        return t;
    }

}
