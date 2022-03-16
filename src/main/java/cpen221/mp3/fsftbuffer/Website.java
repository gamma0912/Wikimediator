package cpen221.mp3.fsftbuffer;

public class Website implements Bufferable{

    /**
     * Rep Invariant
     *
     * ID and content are not null
     *
     * --------------------------------------------------
     *
     * Abstraction Function
     *
     * A website object is a cacheable website with
     * ID being its id and content being its text
     */

    private final String ID;
    private final String content;

    public Website(String id, String content) {
        this.content = content;
        this.ID = id;
    }
    public String id() {
        return this.ID;
    }

    public String getContent() {
        return content;
    }

}
