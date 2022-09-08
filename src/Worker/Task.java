package Worker;

public class Task {
    long rest_time, index;

    Task(long rest_time) {
        this.rest_time = rest_time;
    }

    public long getRest_time() {
        return rest_time;
    }

    public long getIndex() {
        return index;
    }

    public void setIndex(long index) {
        this.index = index;
    }
}
