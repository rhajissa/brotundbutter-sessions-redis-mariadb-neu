package hbv.web;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

public class LibraryDao {
    private final DataSource ds;

    // Inner Classes for Data Transfer
    public static class MemberInfo {
        public int id;
        public String name;
        public String code;
        public MemberInfo(int id, String name, String code) {
            this.id = id;
            this.name = name;
            this.code = code;
        }
    }

    public static class ExemplarInfo {
        public int id;
        public int code;
        public String title;
        public String author;
        public boolean isBorrowed;
        public ExemplarInfo(int id, int code, String title, String author, boolean isBorrowed) {
            this.id = id;
            this.code = code;
            this.title = title;
            this.author = author;
            this.isBorrowed = isBorrowed;
        }
    }

    public static class BorrowedBook {
        public String code;
        public String title;
        public String author;
        public BorrowedBook(String code, String title, String author) {
            this.code = code;
            this.title = title;
            this.author = author;
        }
    }

    public static class BorrowResult {
        public String memberCode;
        public String memberName;
        public List<BorrowedBook> books;
        public BorrowResult(String memberCode, String memberName, List<BorrowedBook> books) {
            this.memberCode = memberCode;
            this.memberName = memberName;
            this.books = books;
        }
    }

    public static class ReturnInfo {
        public int code;
        public String title;
        public String author;
        public String memberCode;
        public String memberName;
        public Timestamp dueDate;
        public boolean isOverdue;
        public String timeDiffText;
        public ReturnInfo(int code, String title, String author, String memberCode, String memberName, Timestamp dueDate, boolean isOverdue, String timeDiffText) {
            this.code = code;
            this.title = title;
            this.author = author;
            this.memberCode = memberCode;
            this.memberName = memberName;
            this.dueDate = dueDate;
            this.isOverdue = isOverdue;
            this.timeDiffText = timeDiffText;
        }
    }

    public static class ReturnedBook {
        public String code;
        public String title;
        public String author;
        public boolean overdue;
        public long overdueDays;
        public ReturnedBook(String code, String title, String author, boolean overdue, long overdueDays) {
            this.code = code;
            this.title = title;
            this.author = author;
            this.overdue = overdue;
            this.overdueDays = overdueDays;
        }
    }

    public static class ReturnResult {
        public String memberCode;
        public String memberName;
        public List<ReturnedBook> books;
        public ReturnResult(String memberCode, String memberName, List<ReturnedBook> books) {
            this.memberCode = memberCode;
            this.memberName = memberName;
            this.books = books;
        }
    }

    public LibraryDao(DataSource ds) {
        this.ds = ds;
    }

    // 1. Check if member exists
    public MemberInfo checkMember(String code) throws SQLException {
        if (code == null || code.trim().isEmpty()) {
            return null;
        }
        String sql = "SELECT id, name, code FROM BIB_Mitglied WHERE code = ?";
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, code.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new MemberInfo(rs.getInt("id"), rs.getString("name"), rs.getString("code"));
                }
            }
        }
        return null;
    }

    // 2. Check if book copy exists and its availability
    public ExemplarInfo checkExemplar(int code) throws SQLException {
        String sql = "select be.id, be.code, b.titel, a.autoren, " +
                     "(select count(*) from BIB_Ausleihe au where au.buchexemplar_id = be.id and au.tatsaechliche_rueckgabe is null) as ausgeliehen " +
                     "from BIB_Buchexemplar be " +
                     "left join BIB_Buch b ON be.buch_id = b.id " +
                     "left join (" +
                     "  select ab.buch_id, group_concat(a.name SEPARATOR '-') as autoren " +
                     "  from BIB_Autorin_Buch ab " +
                     "  join BIB_Autorin a on a.id = ab.autorin_id " +
                     "  group by ab.buch_id " +
                     ") a on a.buch_id = b.id " +
                     "where be.code = ?";

        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    boolean isBorrowed = rs.getInt("ausgeliehen") > 0;
                    String title = rs.getString("titel");
                    String author = rs.getString("autoren");
                    if (author == null) author = "Unbekannt";
                    return new ExemplarInfo(rs.getInt("id"), rs.getInt("code"), title, author, isBorrowed);
                }
            }
        }
        return null;
    }

    // 3. executeBorrowTransaction (Transactional, Pessimistic Locking via SELECT FOR UPDATE)
    public BorrowResult executeBorrowTransaction(String memberCode, String[] exemplarCodes) throws Exception {
        Connection conn = null;
        try {
            conn = ds.getConnection();
            conn.setAutoCommit(false);

            // 1. Get Member
            int memberId = -1;
            String memberName = null;
            String memberSql = "SELECT id, name FROM BIB_Mitglied WHERE code = ?";
            try (PreparedStatement ps = conn.prepareStatement(memberSql)) {
                ps.setString(1, memberCode);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        memberId = rs.getInt("id");
                        memberName = rs.getString("name");
                    } else {
                        throw new IllegalArgumentException("Mitglied nicht gefunden: " + memberCode);
                    }
                }
            }

            List<BorrowedBook> borrowedBooks = new ArrayList<>();
            String insertSql = "INSERT INTO BIB_Ausleihe (mitglied_id, buchexemplar_id, ausleihdatum, rueckgabe_frist) VALUES (?, ?, NOW(), DATE_ADD(NOW(), INTERVAL 14 DAY))";

            for (String codeStr : exemplarCodes) {
                int code = Integer.parseInt(codeStr.trim());

                // 2. Lock and Check Exemplar
                int exemplarId = -1;
                String title = "";
                String author = "";
                String selectSql = "select be.id, b.titel, a.autoren, " +
                                   "(select count(*) from BIB_Ausleihe au where au.buchexemplar_id = be.id and au.tatsaechliche_rueckgabe is null) as ausgeliehen " +
                                   "from BIB_Buchexemplar be " +
                                   "left join BIB_Buch b ON be.buch_id = b.id " +
                                   "left join (" +
                                   "  select ab.buch_id, group_concat(a.name SEPARATOR '-') as autoren " +
                                   "  from BIB_Autorin_Buch ab " +
                                   "  join BIB_Autorin a on a.id = ab.autorin_id " +
                                   "  group by ab.buch_id " +
                                   ") a on a.buch_id = b.id " +
                                   "where be.code = ? FOR UPDATE";

                try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                    ps.setInt(1, code);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            boolean isBorrowed = rs.getInt("ausgeliehen") > 0;
                            if (isBorrowed) {
                                throw new IllegalStateException("Exemplar " + code + " ist bereits verliehen.");
                            }
                            exemplarId = rs.getInt("id");
                            title = rs.getString("titel");
                            author = rs.getString("autoren");
                            if (author == null) author = "Unbekannt";
                        } else {
                            throw new IllegalArgumentException("Exemplar " + code + " nicht gefunden.");
                        }
                    }
                }

                // 3. Record loan
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    ps.setInt(1, memberId);
                    ps.setInt(2, exemplarId);
                    ps.executeUpdate();
                }

                borrowedBooks.add(new BorrowedBook(codeStr, title, author));
            }

            conn.commit();
            return new BorrowResult(memberCode, memberName, borrowedBooks);

        } catch (Exception e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ex) {}
            }
            throw e;
        } finally {
            if (conn != null) {
                try { conn.close(); } catch (SQLException e) {}
            }
        }
    }

    // 4. checkExemplarForReturn
    public ReturnInfo checkExemplarForReturn(int code) throws SQLException {
        String sql = "select be.id as exemplar_id, b.titel, a.autoren, " +
                     "au.id as loan_id, au.ausleihdatum, au.rueckgabe_frist, " +
                     "m.code as member_code, m.name as member_name " +
                     "from BIB_Buchexemplar be " +
                     "left join BIB_Buch b ON be.buch_id = b.id " +
                     "left join (" +
                     "  select ab.buch_id, group_concat(a.name SEPARATOR '-') as autoren " +
                     "  from BIB_Autorin_Buch ab " +
                     "  join BIB_Autorin a on a.id = ab.autorin_id " +
                     "  group by ab.buch_id " +
                     ") a on a.buch_id = b.id " +
                     "left join BIB_Ausleihe au on au.buchexemplar_id = be.id and au.tatsaechliche_rueckgabe is null " +
                     "left join BIB_Mitglied m on m.id = au.mitglied_id " +
                     "where be.code = ?";

        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String title = rs.getString("titel");
                    String author = rs.getString("autoren");
                    if (author == null) author = "Unbekannt";

                    int loanId = rs.getInt("loan_id");
                    if (rs.wasNull() || loanId <= 0) {
                        return new ReturnInfo(code, title, author, null, null, null, false, "Nicht ausgeliehen");
                    }

                    String memberCode = rs.getString("member_code");
                    String memberName = rs.getString("member_name");
                    Timestamp dueDate = rs.getTimestamp("rueckgabe_frist");

                    long diffMs = dueDate.getTime() - System.currentTimeMillis();
                    boolean isOverdue = diffMs < 0;
                    long overdueDays = Math.abs(diffMs) / (1000 * 60 * 60 * 24);

                    String timeDiffText;
                    if (isOverdue) {
                        timeDiffText = (overdueDays == 0) ? "Heute überfällig" : overdueDays + " Tag(e) überfällig";
                    } else {
                        long remainingDays = diffMs / (1000 * 60 * 60 * 24);
                        timeDiffText = (remainingDays == 0) ? "Heute fällig" : remainingDays + " Tag(e) verbleibend";
                    }

                    return new ReturnInfo(code, title, author, memberCode, memberName, dueDate, isOverdue, timeDiffText);
                }
            }
        }
        return null;
    }

    // 5. executeReturnTransaction (Transactional, Pessimistic Locking via SELECT FOR UPDATE)
    public ReturnResult executeReturnTransaction(String[] exemplarCodes) throws Exception {
        Connection conn = null;
        try {
            conn = ds.getConnection();
            conn.setAutoCommit(false);

            String selectSql = "select au.id as loan_id, m.code as member_code, m.name as member_name, " +
                               "be.id as exemplar_id, b.titel, a.autoren, au.rueckgabe_frist " +
                               "from BIB_Ausleihe au " +
                               "join BIB_Mitglied m on m.id = au.mitglied_id " +
                               "join BIB_Buchexemplar be on be.id = au.buchexemplar_id " +
                               "left join BIB_Buch b on b.id = be.buch_id " +
                               "left join (" +
                               "  select ab.buch_id, group_concat(a.name SEPARATOR '-') as autoren " +
                               "  from BIB_Autorin_Buch ab " +
                               "  join BIB_Autorin a on a.id = ab.autorin_id " +
                               "  group by ab.buch_id " +
                               ") a on a.buch_id = b.id " +
                               "where be.code = ? and au.tatsaechliche_rueckgabe is null FOR UPDATE";

            String updateSql = "UPDATE BIB_Ausleihe SET tatsaechliche_rueckgabe = NOW(), status = 'zurueckgegeben' WHERE id = ?";

            String commonMemberCode = null;
            String commonMemberName = null;
            List<ReturnedBook> returnedBooks = new ArrayList<>();

            for (String codeStr : exemplarCodes) {
                int code = Integer.parseInt(codeStr.trim());
                int loanId = -1;
                String title = "";
                String author = "";
                String memberCode = "";
                String memberName = "";
                Timestamp dueDate = null;

                try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                    ps.setInt(1, code);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            loanId = rs.getInt("loan_id");
                            title = rs.getString("titel");
                            author = rs.getString("autoren");
                            if (author == null) author = "Unbekannt";
                            memberCode = rs.getString("member_code");
                            memberName = rs.getString("member_name");
                            dueDate = rs.getTimestamp("rueckgabe_frist");

                            if (commonMemberCode == null) {
                                commonMemberCode = memberCode;
                                commonMemberName = memberName;
                            }
                        } else {
                            throw new IllegalStateException("Keine aktive Ausleihe für Exemplar " + code + " gefunden.");
                        }
                    }
                }

                // Update return date
                try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                    ps.setInt(1, loanId);
                    ps.executeUpdate();
                }

                long diffMs = dueDate.getTime() - System.currentTimeMillis();
                boolean isOverdue = diffMs < 0;
                long overdueDays = isOverdue ? (Math.abs(diffMs) / (1000 * 60 * 60 * 24)) : 0;

                returnedBooks.add(new ReturnedBook(codeStr, title, author, isOverdue, overdueDays));
            }

            conn.commit();
            return new ReturnResult(commonMemberCode, commonMemberName, returnedBooks);

        } catch (Exception e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ex) {}
            }
            throw e;
        } finally {
            if (conn != null) {
                try { conn.close(); } catch (SQLException e) {}
            }
        }
    }
}
