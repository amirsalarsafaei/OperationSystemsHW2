package MainServer;

import DeadLock.DeadLockChecker;
import DeadLock.DeadLockHandlingType;
import Utils.Logger;
import Utils.StreamHandler;
import Worker.Work;
import Worker.WorkerInfo;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

import static java.lang.System.exit;
import static java.lang.Thread.sleep;

public class MainServer {
    private static String []comArgs;
    static Work[] works, worksBackUp;
    static WorkerInfo[]workers;
    static WorkerCom []workerComs;
    private static DeadLockChecker deadLockChecker;
    private static int masterPort, storagePort;
    private static ServerSocket masterSocket;
    private static final ProcessBuilder processBuilder = new ProcessBuilder();
    private static SchedulingAlgorithm schedulingAlgorithm;
    private static boolean []workIsDone;
    private static DeadLockHandlingType deadLockHandling;
    private static StreamHandler storageStream;
    private static final List<Process> processes = new ArrayList<>();
    private static final Logger logger = new Logger("MainServer");
    public static WorkerInfo createWorker(int id) throws IOException {
        List<String> args = new ArrayList<>(List.of(comArgs));
        args.add("Worker.Worker");
        args.add(String.valueOf(id));
        args.add(String.valueOf(masterPort));
        args.add(String.valueOf(storagePort));
        processes.add(processBuilder.command(args).inheritIO().start());
        logger.info("worker " + id + " process started");
        Socket socket = masterSocket.accept();
        logger.info("worker " + id + " socket accepted");
        StreamHandler streamHandler = new StreamHandler(socket);
        return new WorkerInfo(null, socket, streamHandler, id);
    }


    public static void createMemoryManager(String memory) throws IOException, InterruptedException {
        List<String> args = new ArrayList<>(List.of(comArgs));
        args.add("MemoryManager.MemoryManager");
        args.add(String.valueOf(storagePort));
        args.add(String.valueOf(workers.length));
        args.add(memory);
        args.add(String.valueOf(works.length));
        args.add(deadLockHandling.name());
        for (Work i : works)
            args.add(i.encode());
        processes.add(processBuilder.command(args).inheritIO().start());
        sleep(1000);
        storageStream = new StreamHandler(new Socket("localhost", storagePort));
        storageStream.sendMessage("master");
    }


    public static void setup() throws Exception {

        logger.info("starting setup");

        Runtime.getRuntime().addShutdownHook(new ShutDownHook());
        Scanner scanner = new Scanner(System.in);
        int n = Integer.parseInt(scanner.nextLine());
        comArgs =new String[n];
        for (int i = 0; i < n; i++) {
            comArgs[i] = scanner.nextLine();
        }
        masterPort = Integer.parseInt(scanner.nextLine());
        masterSocket = new ServerSocket(masterPort);
        int WorkerCnt = Integer.parseInt(scanner.nextLine());
        String schedulingAlgo = scanner.nextLine();
        int run_interval = 0;
        if(schedulingAlgo.equals("RR")) {
            run_interval = Integer.parseInt(scanner.nextLine());
        }
        deadLockHandling = DeadLockHandlingType.convert(scanner.nextLine());
        storagePort = Integer.parseInt(scanner.nextLine());
        String mem = scanner.nextLine();

        workerComs = new WorkerCom[WorkerCnt];

        int workCnt = Integer.parseInt(scanner.nextLine());

        works = new Work[workCnt];
        worksBackUp = new Work[workCnt];
        workIsDone = new boolean[workCnt];
        workers = new WorkerInfo[WorkerCnt];
        logger.info("Creating works");
        for (int i = 0;i < workCnt; i++) {
            String tmpLine = scanner.nextLine();
            works[i] = new Work(i + " |" +tmpLine + " |" + 0 + " |");
            worksBackUp[i] = new Work(i + " |" + tmpLine + " |" + 0 + " |");
            logger.info("work " + i + " created");
        }
        logger.info("Works created");
        logger.info("creating memory manager");
        createMemoryManager(mem);
        logger.info("created memory manager");
        for (int i = 0; i < WorkerCnt; i++) {
            try {
                workers[i] = createWorker(i);

                workerComs[i] = new WorkerCom(workers[i]);
                logger.info("worker " + i + " created");
                new Thread(workerComs[i]).start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        logger.info("workers started");
        sleep(100);

        switch (schedulingAlgo) {
            case "FCFS" -> schedulingAlgorithm = new FCFS();
            case "SJF" -> schedulingAlgorithm = new SJF();
            case "RR" -> schedulingAlgorithm = new RR(run_interval);
        }
        logger.info("starting schedule ");
        schedulingAlgorithm.start();
        logger.info("scheduling started");
        deadLockChecker = new DeadLockChecker(storageStream);
        deadLockChecker.start();
    }

    public static void deadLockHappened() {
        logger.info("deadlock happened");
        synchronized (schedulingAlgorithm.getLock()) {
            storageStream.sendMessage("deadlock_removal");
            String response = storageStream.getResponse();
            int removal = Integer.parseInt(response);
            for (int i = 0; i < removal; i++) {
                if (works[i].assignedTo != null)
                    works[i].assignedTo.streamHandler.sendMessage("kill#"+i+ "#"+worksBackUp[i].encode());
            }
        }
    }

    static class FCFS implements SchedulingAlgorithm {
        Object lock;
        LinkedList<Work> readyQueue;
        LinkedList<WorkerInfo> freeWorkers;
        TreeSet<Work> runningQueue;
        TreeSet<WorkerInfo> busyWorkers;
        public FCFS() {
            runningQueue = new TreeSet<>();
            freeWorkers = new LinkedList<>();
            readyQueue = new LinkedList<>();
            busyWorkers = new TreeSet<>();
            lock = new Object();
            readyQueue.addAll(Arrays.asList(works));
            freeWorkers.addAll(Arrays.asList(workers));
        }

        @Override
        public Object getLock() {
            return lock;
        }

        @Override
        public Collection<Work> getRunningQueue() {
            return runningQueue;
        }

        @Override
        public Collection<Work> getReadyQueue() {
            return readyQueue;
        }

        @Override
        public Collection<WorkerInfo> getBusyWorkers() {
            return busyWorkers;
        }

        @Override
        public Collection<WorkerInfo> getFreeWorkers() {
            return freeWorkers;
        }

        @Override
        public void reschedule() {
            logger.info("rescheduling");
            Work work = null;
            WorkerInfo worker = null;
            synchronized (getLock()) {
                if (!readyQueue.isEmpty() && !freeWorkers.isEmpty()) {
                    work = readyQueue.removeFirst();
                    worker = freeWorkers.removeFirst();
                    logger.info("scheduler found a free worker " + worker.getId() + "  and free ready work " + work.getWorkId());
                    if  (!assignWork(work, worker)) {
                        freeWorkers.add(worker);
                        reschedule();
                        readyQueue.add(work);
                    }
                }
            }
        }

        @Override
        public void start() {
            for (int i = 0; i < workers.length; i++)
                reschedule();
        }
    }

    static class SJF implements SchedulingAlgorithm {
        Object lock;
        TreeSet<Work> runningQueue, readyQueue;
        TreeSet<WorkerInfo> busyWorkers, freeWorkers;
        SJF(){
            lock = new Object();
            runningQueue = new TreeSet<>();
            readyQueue = new TreeSet<>();
            busyWorkers = new TreeSet<>();
            freeWorkers = new TreeSet<>();
            readyQueue.addAll(Arrays.asList(works));
            freeWorkers.addAll(Arrays.asList(workers));
        }
        @Override
        public Object getLock() {
            return lock;
        }

        @Override
        public Collection<Work> getRunningQueue() {
            return runningQueue;
        }

        @Override
        public Collection<Work> getReadyQueue() {
            return readyQueue;
        }

        @Override
        public Collection<WorkerInfo> getBusyWorkers() {
            return busyWorkers;
        }

        @Override
        public Collection<WorkerInfo> getFreeWorkers() {
            return freeWorkers;
        }

        @Override
        public void reschedule() {
            Work work = null;
            WorkerInfo worker = null;
            synchronized (getLock()) {
                if (!readyQueue.isEmpty() && !freeWorkers.isEmpty()) {
                    ArrayList<Work> tmp = new ArrayList<>();
                    boolean flag = false;
                    do {
                        flag = false;
                        work = readyQueue.pollFirst();
                        worker = freeWorkers.pollFirst();
                        logger.info("scheduler found a free worker " + worker.getId() + "  and free ready work " + work.getWorkId());
                        if (!assignWork(work, worker)) {
                            freeWorkers.add(worker);
                            tmp.add(work);
                            flag = true;
                        }
                    }while (flag && !getReadyQueue().isEmpty() && !getFreeWorkers().isEmpty());
                    for (Work i : tmp)
                        readyQueue.add(i);
                    if (!flag)
                        reschedule();
                }
            }


        }

        @Override
        public void start() {
            for (int i = 0; i < workers.length; i++)
                reschedule();
        }
    }



    static class RR implements SchedulingAlgorithm {
        Object lock;
        LinkedList<Work> readyQueue;
        LinkedList<WorkerInfo> freeWorkers;
        TreeSet<Work> runningQueue;
        TreeSet<WorkerInfo> busyWorkers;
        int running_time;
        RR(int running_time) {
            this.running_time = running_time;
            lock = new Object();
            readyQueue = new LinkedList<>();
            freeWorkers = new LinkedList<>();
            runningQueue = new TreeSet<>();
            busyWorkers = new TreeSet<>();
            readyQueue.addAll(Arrays.asList(works));
            freeWorkers.addAll(Arrays.asList(workers));
        }
        @Override
        public Object getLock() {
            return lock;
        }

        @Override
        public Collection<Work> getRunningQueue() {
            return runningQueue;
        }

        @Override
        public Collection<Work> getReadyQueue() {
            return readyQueue;
        }

        @Override
        public Collection<WorkerInfo> getBusyWorkers() {
            return busyWorkers;
        }

        @Override
        public Collection<WorkerInfo> getFreeWorkers() {
            return freeWorkers;
        }

        @Override
        public  void reschedule() {
            logger.info("rescheduling");
            Work work = null;
            WorkerInfo worker = null;
            boolean flag = false, flag2 = false;
            synchronized (getLock()) {
                if (!freeWorkers.isEmpty() && !readyQueue.isEmpty()) {
                    work = readyQueue.removeFirst();
                    worker = freeWorkers.removeFirst();
                    logger.info("scheduler found a free worker " + worker.getId() + "  and free ready work " + work.getWorkId());

                    flag = true;
                    if  (!assignWork(work, worker)) {
                        freeWorkers.add(worker);
                        reschedule();
                        readyQueue.add(work);
                    }
                }
            }
            if (!flag) {
                logger.info("scheduler didn't found a free worker and free ready queue");
                return;
            }

            final int workId = work.getWorkId();
            WorkerInfo finalWorker = worker;
            new Thread(()->{
                logger.info("started callback thread to call back work : "   + workId + " from worker : " +
                        finalWorker.getId());
                try {
                    sleep(running_time);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if (!workIsDone[workId]) {
                    logger.info("called back work : "   + workId + " from worker : " + finalWorker.getId());
                    finalWorker.streamHandler.sendMessage("stop#"+workId);
                }
            }).start();
            reschedule();
        }

        @Override
        public void start() {
            for (int i = 0; i < workers.length; i++)
                reschedule();
        }
    }
    interface SchedulingAlgorithm {
        Object getLock();
        Collection<Work> getRunningQueue();
        Collection<Work> getReadyQueue();
        Collection<WorkerInfo> getBusyWorkers();
        Collection<WorkerInfo> getFreeWorkers();

        default void workIsBack(String workStr) throws Exception {
            Work work = new Work(workStr);
            logger.info(work.workId + "is back");
            boolean flag = false;
            synchronized (getLock()) {
                int i = work.workId;
                if (work.tasks.isEmpty()) {
                    System.out.println("task " + i + " executed successfully with result " + work.sum);
                    workIsDone[i] = true;
                    removeFromRunningQueue(works[i]);
                    works[i] = work;

                } else {
                    removeFromRunningQueue(works[i]);
                    works[i] = work;
                    flag = true;
                }
            }
            reschedule();
            if (flag) {
                sleep(5);
                addToReadyQueue(work);
            }
            reschedule();
        }
        default void removeFromRunningQueue(Work work) {
            synchronized (getLock()) {
                getRunningQueue().remove(work);
                getBusyWorkers().remove(work.assignedTo);
                getFreeWorkers().add(work.assignedTo);
            }
        }
        default void addToReadyQueue(Work work) {
            synchronized (getLock()) {
                getReadyQueue().add(work);
            }
        }

        default boolean assignWork(Work work, WorkerInfo worker) {
            logger.info("assigning work");
            if (work == null || worker == null) {
                logger.warn("assign failed invalid work or worker");
                return false;
            }
            logger.info("getting dead lock permission from storage");
            String response;
            synchronized (this) {
                storageStream.sendMessage("dead_lock_permission " + work.workId);
                response = storageStream.getResponse();
            }
            if (response.equals("granted")) {
                logger.info("storage granted running");
                worker.assignedWork = work;
                work.assignedTo = worker;
                getRunningQueue().add(work);
                getBusyWorkers().add(worker);
                logger.info("sending message of assigning work : " + work.getWorkId() + " to worker " + worker.getId());
                worker.streamHandler.sendMessage("run#" + work.encode());
                return true;
            }
            logger.info("storage denied running");
            return false;
        }

        void reschedule();

        void start();
    }



    static class WorkerCom implements Runnable {
        WorkerInfo workerInfo;
        String response;
        public WorkerCom(WorkerInfo workerInfo) {
            this.workerInfo = workerInfo;
        }
        @Override
        public void run() {
            while (!workerInfo.socket.isClosed()) {
                response = workerInfo.streamHandler.getResponse();

                String finalTmp = response;
                new Thread(()-> {
                    try {
                        schedulingAlgorithm.workIsBack(finalTmp);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }).start();

            }
        }
    }
    static class ShutDownHook extends Thread {
        @Override
        public void run() {
            for (Process process : processes)
                process.destroyForcibly();
            logger.close();
        }
    }
}


