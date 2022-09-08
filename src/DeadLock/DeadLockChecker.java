package DeadLock;

import MainServer.MainServer;
import Utils.StreamHandler;

public class DeadLockChecker extends Thread{
    private StreamHandler storageStream;
    public DeadLockChecker(StreamHandler storageStream) {
        this.storageStream =storageStream;
    }
    @Override
    public void run() {
        while (true) {
            try {
                sleep(1500);

                if (checkDeadLock()) {
                    MainServer.deadLockHappened();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
    public boolean checkDeadLock() {
        storageStream.sendMessage("deadlock_check");
        return Boolean.parseBoolean(storageStream.getResponse());
    }
}
