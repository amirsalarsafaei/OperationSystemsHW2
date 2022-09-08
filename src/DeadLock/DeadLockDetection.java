package DeadLock;


import java.util.ArrayList;
import java.util.function.Predicate;

public class DeadLockDetection implements DeadLockHandling {
    private final int workCnt, resourceCnt;
    private final ArrayList<Integer>[] neighbours;
    private final boolean[] seen;
    private ArrayList<Integer> topoSort;
    private int removalInt = 0;
    public DeadLockDetection(int workCnt, int resourceCnt) {
        this.workCnt = workCnt;
        this.resourceCnt = resourceCnt;
        this.neighbours = new ArrayList[workCnt + resourceCnt];
        System.out.println(workCnt + resourceCnt);
        for (int i = 0; i < workCnt + resourceCnt; i++)
            neighbours[i] = new ArrayList<>();
        seen = new boolean[workCnt + resourceCnt];
    }


    @Override
    public boolean canRunWork(int workID) {
        return true;
    }

    @Override
    public boolean inDeadLock() {
        return checkForCycle();
    }
    @Override
    public int deadLockRemoval() {
        for (removalInt = 0; removalInt < workCnt; removalInt++) {
            if (!checkForCycle()) {
                break;
            }
        }
        int tmp = removalInt;
        removalInt = 0;
        return tmp;
    }


    public boolean canAllocate(int workID, int resourceID) {
        lockedResource(workID, resourceID);
        boolean ans = checkForCycle();
        releasedResource(workID, resourceID);
        return ans;
    }

    boolean checkForCycle() {
        topoSort = new ArrayList<>();
        for (int i = 0; i < workCnt + resourceCnt; i++) {
            seen[i] = false;
        }
        for (int i = removalInt; i < workCnt + resourceCnt; i++) {
            if (!seen[i])
                dfs(i);
        }
        for (int i = 0; i < workCnt + resourceCnt; i++) {
            seen[i] = false;
        }
        for (int i = 0; i < topoSort.size(); i++) {
            int v = topoSort.get(i);
            seen[v] = true;
            for (int j : neighbours[v]) {
                if (j < removalInt)
                    continue;
                if (!seen[j])
                    return true;
            }
        }
        return false;
    }

    void dfs(int v) {
        seen[v] = true;
        for (int u : neighbours[v]) {
            if (u< removalInt)
                continue;
            if (!seen[u])
                dfs(u);
        }
        topoSort.add(v);
    }

    @Override
    public void lockedResource(int workID, int resourceID) {
        neighbours[getResourceVertex(resourceID)].removeIf(new Predicate<Integer>() {
            @Override
            public boolean test(Integer integer) {
                return integer.equals(getWorkVertex(workID));
            }
        });
        neighbours[getWorkVertex(workID)].add(getResourceVertex(resourceID));
    }

    @Override
    public void releasedResource(int workID, int resourceID) {
        neighbours[getWorkVertex(workID)].remove(new Predicate<Integer>() {
            @Override
            public boolean test(Integer integer) {
                return integer.equals(getResourceVertex(resourceID));
            }
        });
    }

    @Override
    public void runningWork(int workID) {

    }

    @Override
    public void stoppedWork(int workID) {

    }



    @Override
    public void requestedResource(int workID, int resourceID) {
        neighbours[getResourceVertex(resourceID)].add(getWorkVertex(workID));
    }

    int getResourceVertex(int resourceId) {
        return resourceId + workCnt;
    }

    int getWorkVertex(int workId) {
        return workId;
    }
}
