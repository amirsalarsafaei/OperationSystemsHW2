package Worker;


import Utils.StreamHandler;

import java.net.Socket;

public class WorkerInfo implements Comparable<WorkerInfo> {
    public Work assignedWork;
    public Socket socket;
    public StreamHandler streamHandler;
    private final int id;

    public Work getAssignedWork() {
        return assignedWork;
    }

    public void setAssignedWork(Work assignedWork) {
        this.assignedWork = assignedWork;
    }

    public Socket getSocket() {
        return socket;
    }

    public void setSocket(Socket socket) {
        this.socket = socket;
    }

    public StreamHandler getStreamHandler() {
        return streamHandler;
    }

    public void setStreamHandler(StreamHandler streamHandler) {
        this.streamHandler = streamHandler;
    }

    public int getId() {
        return id;
    }

    public WorkerInfo(Work assignedWork, Socket socket, StreamHandler streamHandler, int id) {
        this.assignedWork = assignedWork;
        this.socket = socket;
        this.streamHandler = streamHandler;
        this.id = id;
    }

    @Override
    public int compareTo(WorkerInfo o) {
        return Integer.compare(id, o.id);
    }
}
