package Utils;

import java.io.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;

public class Logger {
    private String name;
    private PrintStream printStream;
    private DateTimeFormatter dateTimeFormatter;
    public Logger(String name) {
        this.name = name;
        File file = new File("./logs/" + name + ".txt");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        try {
            printStream = new PrintStream(new FileOutputStream(file, true));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        dateTimeFormatter =  DateTimeFormatter.ofPattern("EEEE, MMM dd, yyyy HH:mm:ss a");
    }

    public synchronized void info(String a) {
        printStream.println("info\t" +  LocalDateTime.now().format(dateTimeFormatter) + " :\t\t" + a);

    }

    public synchronized  void warn(String a) {
        printStream.println("warn\t" +  LocalDateTime.now().format(dateTimeFormatter) + " :\t\t" + a);

    }

    public synchronized void severe(String a) {
        printStream.println("severe\t" + LocalDateTime.now().format(dateTimeFormatter) + " :\t\t" + a);

    }

    public synchronized void debug(String a) {
        printStream.println("debug\t" + LocalDateTime.now().format(dateTimeFormatter) + " :\t\t" + a);

    }

    public void close() {
        printStream.close();
    }
}
