package MemoryManager;


import DeadLock.*;
import Utils.Logger;
import Utils.StreamHandler;
import Worker.Work;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

public class MemoryManager {
    static ServerSocket workersServerSocket;
    static int workerCnt, memorySize, workCnt;
    static int []memory;
    static SmartLock []memoryLock;
    static WorkConnection []workConnections;
    static WorkerConnection []workerConnections;
    static DeadLockHandlingType deadLockHandlingType;
    static DeadLockHandling deadLockHandling;
    static Logger logger = new Logger("Storage");
    public static void main(String[] args) {

        try {
            logger.info("setup started");
            workersServerSocket = new ServerSocket(Integer.parseInt(args[0]));
            workerCnt = Integer.parseInt(args[1]);
            workerConnections = new WorkerConnection[workerCnt];
            String []str = args[2].split(" ");
            memorySize = str.length;
            memory = new int[memorySize];
            memoryLock = new SmartLock[memorySize];
            for (int i = 0; i < memorySize; i++)
                 memory[i] = Integer.parseInt(str[i]);
            workCnt = Integer.parseInt(args[3]);
            workConnections = new WorkConnection[workCnt];
            deadLockHandlingType = DeadLockHandlingType.valueOf(args[4]);
            for (int i = 0; i < memorySize; i++)
                memoryLock[i] = new SmartLock(i);
            ArrayList<Work> works = new ArrayList<>();
            for (int i = 0; i < workCnt; i++) {
                try {
                    works.add(new Work(args[5+i]));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            switch (deadLockHandlingType) {
                case none -> deadLockHandling = new DeadLockNone();
                case detection -> deadLockHandling = new DeadLockDetection(workCnt, memorySize);
                case prevention -> deadLockHandling = new DeadLockPrevention(memorySize, workCnt, works);
            }
            logger.info("WorkCnt :" + workCnt + ", MemSize : " + memorySize);
            logger.info("setup complete");
        } catch (IOException e) {
            e.printStackTrace();
        }
        while (true) {
            try {
                logger.info("socket accept while started");
                Socket socket = workersServerSocket.accept();
                logger.info("accepted a socket");
                StreamHandler streamHandler = new StreamHandler(socket);
                logger.info("waiting to get socket type");
                String[] str = streamHandler.getResponse().split(" ");
                switch (str[0]) {
                    case "worker" -> {
                        int worker_id = Integer.parseInt(str[1]);
                        logger.info("socket is from worker");
                        workerConnections[worker_id] = new WorkerConnection(streamHandler, worker_id);
                        workerConnections[worker_id].start();
                    }
                    case "work" -> {
                        int work_id = Integer.parseInt(str[1]);
                        logger.info("socket is from work");
                        workConnections[work_id] = new WorkConnection(streamHandler, work_id);
                        workConnections[work_id].start();
                    }
                    case "master" -> {
                        logger.info("socket is from master");
                        new MasterConnection(streamHandler).start();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    static class MasterConnection extends Thread {
        StreamHandler streamHandler;
        MasterConnection(StreamHandler streamHandler) {
            this.streamHandler = streamHandler;
        }
        @Override
        public void run() {
            while (true) {
                logger.info("MasterConnectionThread : started master connection loop");
                String[] str = streamHandler.getResponse().split(" ");
                if (str.length == 0) {
                    logger.warn("MasterConnectionThread : invalid response from master");
                    continue;
                }
                String operation = str[0];
                synchronized (deadLockHandling) {
                    switch (operation) {
                        case "dead_lock_permission" -> {
                            int work_id = Integer.parseInt(str[1]);
                            logger.info("MasterConnectionThread : master wants dead lock permission for work " + str[1]);

                            if (deadLockHandling.canRunWork(work_id)) {
                                deadLockHandling.runningWork(work_id);
                                logger.info("MasterConnectionThread : granting master permission to run work " + str[1]);
                                streamHandler.sendMessage("granted");
                            } else {
                                logger.info("MasterConnectionThread : denied master permission to run work " + str[1]);
                                streamHandler.sendMessage("denied");
                            }
                        }
                        case "deadlock_check" -> streamHandler.sendMessage(String.valueOf(deadLockHandling.inDeadLock()));
                        case "deadlock_removal" -> streamHandler.sendMessage(String.valueOf(deadLockHandling.deadLockRemoval()));
                    }
                }
            }
        }
    }
    static class WorkerConnection extends Thread {
        StreamHandler streamHandler;
        int workerID;
        WorkerConnection(StreamHandler streamHandler, int workerID) {
            this.streamHandler = streamHandler;
            this.workerID = workerID;
        }
        @Override
        public void run() {
            logger.info("WorkerConnectionThread : starting worker connection loop");
            while (true) {
                String[] str = streamHandler.getResponse().split("#");
                if (str.length == 0) {
                    continue;
                }
                String operation = str[0];
                int work_id = Integer.parseInt(str[1]);
                if ("stopped".equals(operation)) {
                    workConnections[work_id].streamHandler.close();
                    workConnections[work_id].run = false;
                    workConnections[work_id].interrupt();
                    synchronized (deadLockHandling) {
                        deadLockHandling.stoppedWork(work_id);
                    }
                }
                else if ("killed".equals(operation)) {
                    workConnections[work_id].streamHandler.close();
                    workConnections[work_id].run = false;
                    workConnections[work_id].interrupt();
                    String []lockedRes = str[2].split(" ");
                    for (String i : lockedRes) {
                        int resourceID = Integer.parseInt(i);
                        memoryLock[resourceID].Release(work_id);
                    }
                }
            }
        }
    }

    static class WorkConnection extends Thread{
        StreamHandler streamHandler;
        int workID;
        boolean run;
        WorkConnection(StreamHandler streamHandler, int workID) {
            this.streamHandler = streamHandler;
            this.workID = workID;
            run = true;
        }
        @Override
        public void run() {
            logger.info("WorkConnectionThread(work : " + workID + ") : " + "starting work connection loop");
            while (run) {
                logger.info("WorkConnectionThread(work : " + workID + ") : " + "waiting for response from work");
                String response = (streamHandler.getResponse());
                String[] str = response.split(" ");
                if (str.length == 0) {
                    return;
                }
                String operation = str[0];
                logger.info("WorkConnectionThread(work : " + workID + ") : " + "requested operation [" + operation+"]");

                switch (operation) {
                    case "write" -> {
                        int memory_index = Integer.parseInt(str[1]);
                        int value = Integer.parseInt(str[2]);
                        synchronized (memoryLock[memory_index]) {
                            if (memoryLock[memory_index].lockerID == workID) {
                                memory[memory_index] = value;
                            }
                        }
                    }
                    case "obtain" -> {

                        int memory_index = Integer.parseInt(str[1]);
                        boolean locked = false;

                        try {
                            logger.info("WorkConnectionThread(work : " + workID + ") : " + "locking " +
                                            "memory[" + memory_index +"]");
                            memoryLock[memory_index].Lock(workID);
                            logger.info("WorkConnectionThread(work : " + workID + ") : " + "locked " +
                                    "memory[" + memory_index +"]");
                            locked = true;
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        if (locked) {
                            streamHandler.sendMessage(String.valueOf(memory[memory_index]));
                        } else {
                            streamHandler.sendMessage("obtain failed");
                        }


                    }
                    case "release" -> {
                        int memory_index = Integer.parseInt(str[1]);
                        memoryLock[memory_index].Release(workID);
                        logger.info("WorkConnectionThread(work : " + workID + ") : " + "released " +
                                "memory[" + memory_index +"]");
                    }
                }
            }
        }
    }

    static class SmartLock {
        public int  lockerID = -1, lockID;
        private boolean locked = false;
        SmartLock(int lockID) {
            this.lockID = lockID;
        }
        void Lock(int lockerID) throws InterruptedException {
            logger.info(lockerID + " wants to lock " + lockID + " which is locked by " + this.lockerID);
            if (lockerID == this.lockerID)
                return;
            synchronized (deadLockHandling) {
                deadLockHandling.requestedResource(lockerID, lockID);
            }
            synchronized (this) {
                while (locked) {
                    wait();
                }
                locked = true;
                this.lockerID = lockerID;
                synchronized (deadLockHandling) {
                    deadLockHandling.lockedResource(lockerID, lockID);
                }
            }
        }
        void Release(int lockerID) {
            synchronized (this) {
                if (!locked || lockerID != this.lockerID) {
                    return;
                }
                synchronized (deadLockHandling) {
                    deadLockHandling.releasedResource(lockerID, lockID);
                }
                this.lockerID = -1;
                locked = false;
                this.notify();
            }
        }
    }
}