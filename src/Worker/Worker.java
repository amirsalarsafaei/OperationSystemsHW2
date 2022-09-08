package Worker;

import Utils.Logger;
import Utils.StreamHandler;

import java.io.IOException;
import java.net.Socket;

import static java.lang.Thread.sleep;


class Worker {
    static WorkerStatus status = WorkerStatus.StandBy;
    static StreamHandler masterIO, memoryIO;
    static Work work;
    static Thread masterCon;
    static WorkRunThread workRunThread;
    static int worker_id, memory_port;
    static final Object lock = new Object();
    static Logger logger;
    public static void main(String[] args) {
        Socket masterSocket , memorySocket;
        try {
            worker_id = Integer.parseInt(args[0]);
            logger = new Logger("Worker_" + worker_id);
            logger.info("started setup");
            masterSocket = new Socket("localhost", Integer.parseInt(args[1]));
            memory_port = Integer.parseInt(args[2]);
            memorySocket = new Socket("localhost", memory_port);
            masterIO = new StreamHandler(masterSocket);
            memoryIO = new StreamHandler(memorySocket);
            memoryIO.sendMessage("worker " + worker_id);
            logger.info("finished setup");
        } catch (IOException e) {
            System.err.println("Couldn't open socket from worker");
            e.printStackTrace();
        }
        masterCon = new Thread(new MasterConnectionRun());
        masterCon.start();
    }


    public static void startWork() {
        logger.info("starting work " + work.getWorkId());
        workRunThread = new WorkRunThread();
        workRunThread.start();
    }

    public static void stopWork() throws InterruptedException {
        logger.info("stopping work : " + work.getWorkId());
        synchronized (lock) {

            switch (status) {
                case Waiting -> {
                    break;
                }
                case Sleeping -> {
                    logger.info("work is sleeping");
                    if (work.getTasks().getFirst().index == -1) {
                        return;
                    }
                    workRunThread.interrupt();
                    return;
                }
                case Ending -> {
                    return;
                }
                case StandBy -> {
                    logger.warn("Invalid Case, Context Switch on Standby");
                    return;
                }
                default -> throw new IllegalStateException("Unexpected value: " + status);
            }
            //noinspection deprecation
            workRunThread.stop();
            masterIO.sendMessage(work.encode());
            memoryIO.sendMessage("stopped#" + work.workId);
        }
    }



    static class MasterConnectionRun implements Runnable  {

        @Override
        public void run() {
            logger.info("starting master connection loop");
            while(true) {
                logger.info("waiting for masters instruction");
                String []response = masterIO.getResponse().split("#");
                String op = response[0], data = response[1];
                switch (op) {
                    case "stop" -> {
                        logger.info("master requested context switch");
                        if (work == null || Integer.parseInt(data) != work.workId) {
                            logger.info("invalid stop request for " + data);
                            continue;
                        }
                        try {
                            stopWork();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    case "kill" -> {
                        logger.info("master requested kill");
                        if (work == null || Integer.parseInt(data) != work.workId) {
                            logger.info("invalid kill request for " + data);
                            continue;
                        }
                        killWork();
                        masterIO.sendMessage(response[2]);
                        work = null;
                    }
                    case "run" -> {
                        logger.info("got work from master");
                        try {
                            work = new Work(data);
                            startWork();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }


    }
    static void killWork() {
        synchronized (lock) {
            if (work == null) {
                return;
            }
            workRunThread.stop();
            StringBuilder tmp = new StringBuilder();
            for (Long i : work.lockedResources) {
                tmp.append(i);
                tmp.append(" ");
            }
            memoryIO.sendMessage("killed#"+work.workId+"#"+tmp);
        }
    }
    static class WorkRunThread extends Thread {
        StreamHandler storageStream;
        long beforeRest, restTime;
        void rest() {
            synchronized (lock) {
                status = WorkerStatus.Sleeping;
                beforeRest = System.currentTimeMillis();
                restTime = work.tasks.getFirst().rest_time;
            }
            logger.info("WorkRunThread : work sleep time : " + restTime);
            if (restTime <= 0)
                return;
            try {
                sleep(restTime);

                synchronized (lock) {
                    work.tasks.getFirst().rest_time = 0;
                    status = WorkerStatus.Waiting;
                    logger.info("sleep done");
                    if (work.tasks.getFirst().getIndex() == -1) {
                        status = WorkerStatus.Ending;
                        work.tasks.clear();
                    }
                }
            } catch (InterruptedException e) {
                synchronized (lock) {

                    long time_elapsed = System.currentTimeMillis() - beforeRest;
                    logger.info("WorkRunThread : interrupted sleep, time elapsed : " + time_elapsed);
                    work.tasks.getFirst().rest_time = Math.max(0, restTime - time_elapsed);
                    masterIO.sendMessage(work.encode());
                    work = null;
                    stop();
                }
                //e.printStackTrace();
            }
        }

        void getLockAndCompute() {

            long index;
            synchronized (lock) {

                index = work.tasks.getFirst().index;
                logger.info("WorkRunThread : requesting read/lock for memory["+index + "] from storage");
                storageStream.sendMessage("obtain " + index);
                work.lockedResources.add(index);
            }
            logger.info("WorkRunThread : waiting for read response from storage");
            String response = storageStream.getResponse();
            long resource = Integer.parseInt(response);

            synchronized (lock) {
                logger.info("WorkRunThread : started computing");
                work.sum += resource;
                work.tasks.removeFirst();
                if (work.tasks.isEmpty())
                    status = WorkerStatus.Ending;
                logger.info("WorkRunThread : ended computing");
            }
        }

        @Override
        public void run() {
            logger.info("WorkRunThread : starting work run thread");
            try {
                logger.info("WorkRunThread : creating work to storage connection");
                storageStream = new StreamHandler(new Socket("localhost", memory_port));
                storageStream.sendMessage("work " + work.workId);
                logger.info("WorkRunThread : created work to storage connection");
            } catch (IOException e) {
                e.printStackTrace();
                logger.warn("WorkRunThread : error creating work to storage connection");
            }
            logger.info("WorkRunThread : starting running work loop");
            while(true) {
                synchronized (lock) {
                    if (status == WorkerStatus.Ending)
                        break;
                }

                rest();
                synchronized (lock) {
                    if (status == WorkerStatus.Ending)
                        break;
                }
                getLockAndCompute();
            }
            logger.info("WorkRunThread : ended running work loop");
            synchronized (lock) {
                logger.info("WorkRunThread : started releasing locked resources");
                for (long mem_index : work.lockedResources) {
                    storageStream.sendMessage("release " + mem_index);
                    logger.info("WorkRunThread : released lock: " + mem_index);
                }
                work.lockedResources.clear();
                logger.info("WorkRunThread : ended releasing locked resources");
                status = WorkerStatus.StandBy;
                masterIO.sendMessage(work.encode());
            }

        }
    }



    enum WorkerStatus {
        StandBy, Waiting, Sleeping, Ending
    }
}
