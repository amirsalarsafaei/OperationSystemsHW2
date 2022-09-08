package DeadLock;

public class DeadLockNone implements DeadLockHandling {


    @Override
    public boolean canRunWork(int workID) {
        return true;
    }

    @Override
    public boolean inDeadLock() {
        return false;
    }

    @Override
    public int deadLockRemoval() {
        return 0;
    }

    @Override
    public void lockedResource(int workID, int resourceID) {

    }

    @Override
    public void requestedResource(int workID, int resourceID) {

    }

    @Override
    public void releasedResource(int workID, int resourceID) {

    }

    @Override
    public void runningWork(int workID) {

    }

    @Override
    public void stoppedWork(int workID) {

    }




}
