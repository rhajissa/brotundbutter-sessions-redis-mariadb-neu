package hbv.web;

import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class ReturnReceiptCommand implements PrintCommand {
    private String memberCode;
    private String memberName;
    private long timestamp;
    private List<ItemInfo> items;

    public static class ItemInfo {
        public String code;
        public String title;
        public String author;
        public boolean overdue;
        public long overdueDays;

        public ItemInfo(String code, String title, String author, boolean overdue, long overdueDays) {
            this.code = code;
            this.title = title;
            this.author = author;
            this.overdue = overdue;
            this.overdueDays = overdueDays;
        }
    }

    public ReturnReceiptCommand(String memberCode, String memberName, long timestamp, List<ItemInfo> items) {
        this.memberCode = memberCode;
        this.memberName = memberName;
        this.timestamp = timestamp;
        this.items = items;
    }

    @Override
    public void execute(PrintWriter writer) {
        String dateStr = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss").format(new Date(timestamp * 1000));
        writer.println("==========================================");
        writer.println("           RÜCKGABE-QUITTUNG");
        writer.println("==========================================");
        writer.println("Bibliothek Brot & Butter");
        writer.println("Datum:      " + dateStr);
        writer.println("Mitglied:   " + memberName + " (" + memberCode + ")");
        writer.println("------------------------------------------");
        writer.println("Zurückgegebene Exemplare:");
        for (ItemInfo item : items) {
            writer.println("- [" + item.code + "] " + item.title + " (von " + item.author + ")");
            if (item.overdue) {
                writer.println("  Status: ÜBERFÄLLIG um " + item.overdueDays + " Tag(e)");
            } else {
                writer.println("  Status: Fristgerecht zurückgegeben");
            }
        }
        writer.println("------------------------------------------");
        writer.println("Rückgabe erfolgreich registriert.");
        writer.println("==========================================");
        writer.flush();
    }
}
