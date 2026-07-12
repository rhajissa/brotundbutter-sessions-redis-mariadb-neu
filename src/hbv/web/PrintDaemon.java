package hbv.web;

import java.io.*;
import java.util.*;
import redis.clients.jedis.RedisClient;

public class PrintDaemon {

    public static void main(String[] args) {
        System.out.println("SWE II Print Daemon wird gestartet...");

        // 1. Konfiguration laden
        Properties props = new Properties();
        try (InputStream is = new FileInputStream("local/config.txt")) {
            props.load(is);
        } catch (IOException e) {
            System.err.println("Fehler beim Laden von local/config.txt: " + e.getMessage());
            System.exit(1);
        }

        String redisServer = props.getProperty("redisserver", "localhost");
        String redisPassword = props.getProperty("redispassword", "");

        System.out.println("Verbinde mit Redis auf " + redisServer + "...");

        // 2. Jedis initialisieren
        try {
            JedisAdapter.init(redisServer, 6379, redisPassword, 10);
        } catch (Exception e) {
            System.err.println("Fehler bei der Initialisierung des Jedis-Adapters: " + e.getMessage());
            System.exit(1);
        }

        // 3. Verzeichnis für Quittungen anlegen
        File printDir = new File("printed_receipts");
        if (!printDir.exists()) {
            printDir.mkdirs();
        }
        System.out.println("Quittungen werden in '" + printDir.getAbsolutePath() + "' gespeichert.");

        RedisClient redis = JedisAdapter.getClient();
        System.out.println("Warte auf Druckaufträge in der Warteschlange 'print_queue'...");

        // 4. Endlosschleife zum Verarbeiten der Warteschlange
        while (true) {
            try {
                // blockierendes Pop aus der Queue with 5 seconds timeout
                List<String> popped = redis.brpop(5, "print_queue");
                if (popped != null && popped.size() >= 2) {
                    String json = popped.get(1);
                    System.out.println("Empfangener Druckauftrag: " + json);
                    processPrintJob(json, printDir);
                }
            } catch (Exception e) {
                System.err.println("Fehler im Loop: " + e.getMessage());
                try {
                    Thread.sleep(2000); // Bei Fehlern kurz warten, um Log-Spamming zu vermeiden
                } catch (InterruptedException ie) {
                    break;
                }
            }
        }

        JedisAdapter.destroy();
        System.out.println("Print Daemon beendet.");
    }

    private static void processPrintJob(String json, File printDir) {
        try {
            String commandType = JsonHelper.getString(json, "commandType");
            String memberCode = JsonHelper.getString(json, "memberCode");
            String memberName = JsonHelper.getString(json, "memberName");
            long timestamp = JsonHelper.getLong(json, "timestamp");

            PrintCommand command = null;

            if ("BORROW".equals(commandType)) {
                List<String> itemObjects = JsonHelper.getObjectsInArray(json, "items");
                List<BorrowReceiptCommand.ItemInfo> items = new ArrayList<>();
                for (String itemJson : itemObjects) {
                    String code = JsonHelper.getString(itemJson, "code");
                    String title = JsonHelper.getString(itemJson, "title");
                    String author = JsonHelper.getString(itemJson, "author");
                    items.add(new BorrowReceiptCommand.ItemInfo(code, title, author));
                }
                command = new BorrowReceiptCommand(memberCode, memberName, timestamp, items);
            } else if ("RETURN".equals(commandType)) {
                List<String> itemObjects = JsonHelper.getObjectsInArray(json, "items");
                List<ReturnReceiptCommand.ItemInfo> items = new ArrayList<>();
                for (String itemJson : itemObjects) {
                    String code = JsonHelper.getString(itemJson, "code");
                    String title = JsonHelper.getString(itemJson, "title");
                    String author = JsonHelper.getString(itemJson, "author");
                    boolean overdue = JsonHelper.getBoolean(itemJson, "overdue");
                    long overdueDays = JsonHelper.getLong(itemJson, "overdueDays");
                    items.add(new ReturnReceiptCommand.ItemInfo(code, title, author, overdue, overdueDays));
                }
                command = new ReturnReceiptCommand(memberCode, memberName, timestamp, items);
            }

            if (command != null) {
                // Dateiname: quittung_[timestamp]_[mitglieds_code]_[type].txt
                String filename = String.format("quittung_%d_%s_%s.txt", timestamp, memberCode, commandType.toLowerCase());
                File file = new File(printDir, filename);
                try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
                    command.execute(pw);
                    System.out.println("Quittung erfolgreich gedruckt: " + file.getName());
                }
            } else {
                System.err.println("Unbekannter commandType: " + commandType);
            }
        } catch (Exception e) {
            System.err.println("Fehler beim Verarbeiten des Druckauftrags: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
