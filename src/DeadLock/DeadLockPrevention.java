package DeadLock;


import Utils.Logger;
import Worker.Task;
import Worker.Work;

import java.util.ArrayList;
import java.util.Arrays;

public class DeadLockPrevention implements DeadLockHandling {
    boolean[][] needs;
    boolean[][] has;
    boolean[] isAllocated;
    int work_cnt, mem_size;
    static Logger logger = new Logger("DeadLockPrevention");
    public DeadLockPrevention(int mem_size, int work_cnt, ArrayList<Work> works) {
        logger.info("Deadlock prevention started");
        needs = new boolean[work_cnt][mem_size];
        this.work_cnt = work_cnt;
        this.mem_size = mem_size;
        has = new boolean[work_cnt][mem_size];
        isAllocated = new boolean[mem_size];
        for (Work i : works)
            for (Task task : i.getTasks())
                needs[i.getWorkId()][(int) task.getIndex()] = true;
    }


    @Override
    public boolean canRunWork(int workID) {
        logger.debug("Allocated : " + Arrays.toString(isAllocated));
        for (int i = 0; i < mem_size; i++)
            if (needs[workID][i] && isAllocated[i])
                return false;
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
        logger.info(workID + " locked " + resourceID);
        needs[workID][resourceID] = false;
        has[workID][resourceID] = true;
    }

    @Override
    public void requestedResource(int workID, int resourceID) {

    }

    @Override
    public void releasedResource(int workID, int resourceID) {
        logger.info(workID + " released " + resourceID);
        needs[workID][resourceID] = true;
        has[workID][resourceID] = false;
        isAllocated[resourceID] = false;
    }

    @Override
    public void stoppedWork(int workID) {
        for (int i = 0; i < mem_size; i++)
            if (needs[workID][i])
                isAllocated[i] = false;
    }



    @Override
    public void runningWork(int workID) {
        for (int i = 0; i < mem_size; i++)
            if (needs[workID][i])
                isAllocated[i] = true;
    }


}
