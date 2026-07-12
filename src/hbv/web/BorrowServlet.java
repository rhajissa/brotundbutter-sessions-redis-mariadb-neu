package hbv.web;

import java.io.*;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import javax.naming.*;
import javax.sql.*;
import redis.clients.jedis.RedisClient;

public class BorrowServlet extends HttpServlet {
    private LibraryDao dao;

    @Override
    public void init() throws ServletException {
        try {
            Context initCtx = new InitialContext();
            DataSource ds = (DataSource) initCtx.lookup("java:/comp/env/jdbc/mariadb");
            this.dao = new LibraryDao(ds);
        } catch (NamingException e) {
            throw new ServletException("JNDI Lookup fehlgeschlagen", e);
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().print("{\"success\":false,\"message\":\"Nicht autorisiert\"}");
            return;
        }

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();

        String action = request.getParameter("action");
        String code = request.getParameter("code");

        if ("checkMember".equals(action)) {
            try {
                LibraryDao.MemberInfo member = dao.checkMember(code);
                if (member != null) {
                    out.print("{\"success\":true,\"name\":\"" + JsonHelper.escape(member.name) + "\",\"code\":\"" + JsonHelper.escape(member.code) + "\"}");
                } else {
                    out.print("{\"success\":false,\"message\":\"Mitglied nicht gefunden\"}");
                }
            } catch (SQLException e) {
                out.print("{\"success\":false,\"message\":\"Datenbankfehler: " + JsonHelper.escape(e.getMessage()) + "\"}");
            }
        } else if ("checkExemplar".equals(action)) {
            if (code == null || code.trim().isEmpty()) {
                out.print("{\"success\":false,\"message\":\"Code darf nicht leer sein\"}");
                return;
            }
            try {
                int barcode = Integer.parseInt(code.trim());
                LibraryDao.ExemplarInfo exemplar = dao.checkExemplar(barcode);
                if (exemplar != null) {
                    if (exemplar.isBorrowed) {
                        out.print("{\"success\":false,\"message\":\"Dieses Exemplar ist bereits verliehen\"}");
                    } else {
                        out.print("{\"success\":true,\"code\":" + exemplar.code + ",\"title\":\"" + JsonHelper.escape(exemplar.title) + "\",\"author\":\"" + JsonHelper.escape(exemplar.author) + "\"}");
                    }
                } else {
                    out.print("{\"success\":false,\"message\":\"Exemplar nicht gefunden\"}");
                }
            } catch (NumberFormatException e) {
                out.print("{\"success\":false,\"message\":\"Ungültiges Barcode-Format\"}");
            } catch (SQLException e) {
                out.print("{\"success\":false,\"message\":\"Datenbankfehler: " + JsonHelper.escape(e.getMessage()) + "\"}");
            }
        } else {
            out.print("{\"success\":false,\"message\":\"Ungültige Aktion\"}");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().print("{\"success\":false,\"message\":\"Nicht autorisiert\"}");
            return;
        }

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();

        String memberCode = request.getParameter("memberCode");
        String exemplarCodesStr = request.getParameter("exemplarCodes");

        if (memberCode == null || memberCode.trim().isEmpty() || exemplarCodesStr == null || exemplarCodesStr.trim().isEmpty()) {
            out.print("{\"success\":false,\"message\":\"Fehlende Parameter\"}");
            return;
        }

        String[] exemplarCodes = exemplarCodesStr.split(",");
        try {
            LibraryDao.BorrowResult result = dao.executeBorrowTransaction(memberCode, exemplarCodes);

            // Push print job to Redis
            long timestamp = System.currentTimeMillis() / 1000;
            List<String> itemsDetailsJson = new ArrayList<>();
            for (LibraryDao.BorrowedBook book : result.books) {
                itemsDetailsJson.add(String.format("{\"code\":\"%s\",\"title\":\"%s\",\"author\":\"%s\"}",
                        JsonHelper.escape(book.code), JsonHelper.escape(book.title), JsonHelper.escape(book.author)));
            }
            String itemsList = String.join(",", itemsDetailsJson);
            String printCommandJson = String.format(
                "{\"commandType\":\"BORROW\",\"memberCode\":\"%s\",\"memberName\":\"%s\",\"timestamp\":%d,\"items\":[%s]}",
                JsonHelper.escape(result.memberCode), JsonHelper.escape(result.memberName), timestamp, itemsList
            );

            try {
                RedisClient redis = JedisAdapter.getClient();
                redis.lpush("print_queue", printCommandJson);
            } catch (Exception e) {
                System.err.println("Fehler beim Senden des Druckauftrags: " + e.getMessage());
            }

            out.print("{\"success\":true,\"message\":\"Ausleihe erfolgreich durchgeführt\"}");

        } catch (Exception e) {
            out.print("{\"success\":false,\"message\":\"Fehler bei Ausleihe: " + JsonHelper.escape(e.getMessage()) + "\"}");
        }
    }
}
