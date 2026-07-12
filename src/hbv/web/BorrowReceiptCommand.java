package hbv.web;

import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class BorrowReceiptCommand implements PrintCommand {
    private String memberCode;
    private String memberName;
    private long timestamp;
    private List<ItemInfo> items;

    public static class ItemInfo {
        public String code;
        public String title;
        public String author;

        public ItemInfo(String code, String title, String author) {
            this.code = code;
            this.title = title;
            this.author = author;
        }
    }

    public BorrowReceiptCommand(String memberCode, String memberName, long timestamp, List<ItemInfo> items) {
        this.memberCode = memberCode;
        this.memberName = memberName;
        this.timestamp = timestamp;
        this.items = items;
    }

    @Override
    public void execute(PrintWriter writer) {
        String dateStr = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss").format(new Date(timestamp * 1000));
        writer.println("==========================================");
        writer.println("           AUSLEIHE-QUITTUNG");
        writer.println("==========================================");
        writer.println("Bibliothek Brot & Butter");
        writer.println("Datum:      " + dateStr);
        writer.println("Mitglied:   " + memberName + " (" + memberCode + ")");
        writer.println("------------------------------------------");
        writer.println("Ausgeliehene Exemplare:");
        for (ItemInfo item : items) {
            writer.println("- [" + item.code + "] " + item.title + " (von " + item.author + ")");
            writer.println("  Rückgabefrist: in 14 Tagen");
        }
        writer.println("------------------------------------------");
        writer.println("Vielen Dank für Ihren Besuch!");
        writer.println("==========================================");
        writer.flush();
    }
}
