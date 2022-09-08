package DeadLock;

public interface DeadLockHandling {
    boolean canRunWork(int workID);

    boolean inDeadLock();

    int deadLockRemoval();

    void lockedResource(int workID, int resourceID);

    void requestedResource(int workID, int resourceID);

    void releasedResource(int workID, int resourceID);

    void runningWork(int workID);

    void stoppedWork(int workID);


}
