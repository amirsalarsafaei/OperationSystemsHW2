package Worker;


import Utils.Logger;

import java.util.ArrayList;
import java.util.LinkedList;

public class Work implements Comparable<Work> {
    public LinkedList<Task> tasks;
    public long sum = 0;
    long waitTime = 0;
    public int workId;
    ArrayList<Long> lockedResources;
    public WorkerInfo assignedTo;
    static Logger logger = new Logger("Work");
    public String encode() {
        StringBuilder res = new StringBuilder();
        res.append(workId);

        res.append(" |");
        boolean flag = false;
        for (Task task : tasks) {
            if (flag)
                res.append(' ');
            res.append(task.getRest_time());
            if (task.getIndex() != -1) {
                res.append(' ');
                res.append(task.getIndex());
            }
            flag = true;
        }
        if (flag)
            res.append(' ');
        res.append('|');
        res.append(sum);
        res.append(" |");
        for (long a : lockedResources) {
            res.append(a);
            res.append(' ');
        }
        return res.toString();
    }

    public Work(String s) throws Exception {
        tasks = new LinkedList<>();
        lockedResources = new ArrayList<>();
        Task tmpTask = null;
        long tmp = 0;
        boolean workIDSeen = false;
        boolean taskSeen = false;
        boolean sumSeen = false;
        for (char ch : s.toCharArray()) {
            if (ch == '|') {
                if (!workIDSeen) {
                    workIDSeen = true;
                } else if (!taskSeen)
                    taskSeen = true;
                else
                    sumSeen = true;
            } else if (Character.isDigit(ch)) {
                tmp = tmp * 10 + (ch - '0');
            } else {
                if (!workIDSeen) {
                    workId = (int) tmp;
                    tmp = 0;
                } else if (!taskSeen) {
                    if (tmpTask == null) {
                        waitTime += tmp;
                        tmpTask = new Task(tmp);
                        tmp = 0;
                    } else {
                        tmpTask.setIndex(tmp);
                        tmp = 0;
                        tasks.add(tmpTask);
                        tmpTask = null;
                    }
                } else if (!sumSeen) {
                    sum = tmp;
                    tmp = 0;
                } else {
                    lockedResources.add(tmp);
                    tmp = 0;
                }
            }
        }
        if (!sumSeen) {
            throw new Exception("Invalid Work String, Couldn't Decode : " + s);
        }
        if (tmpTask != null) {
            tmpTask.setIndex(-1);
            tasks.add(tmpTask);
        }
        logger.info(s);
    }

    @Override
    public int compareTo(Work o) {
        if (waitTime != o.waitTime)
            return Long.compare(waitTime, o.waitTime);
        return Integer.compare(workId, o.workId);
    }


    public LinkedList<Task> getTasks() {
        return tasks;
    }

    public void setTasks(LinkedList<Task> tasks) {
        this.tasks = tasks;
    }

    public long getSum() {
        return sum;
    }

    public void setSum(long sum) {
        this.sum = sum;
    }

    public long getWaitTime() {
        return waitTime;
    }

    public void setWaitTime(long waitTime) {
        this.waitTime = waitTime;
    }

    public int getWorkId() {
        return workId;
    }

    public void setWorkId(int workId) {
        this.workId = workId;
    }

    public ArrayList<Long> getLockedResources() {
        return lockedResources;
    }

    public void setLockedResources(ArrayList<Long> lockedResources) {
        this.lockedResources = lockedResources;
    }

    public WorkerInfo getAssignedTo() {
        return assignedTo;
    }

    public void setAssignedTo(WorkerInfo assignedTo) {
        this.assignedTo = assignedTo;
    }
}
