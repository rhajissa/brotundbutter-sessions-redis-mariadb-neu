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

public class ReturnServlet extends HttpServlet {
    private LibraryDao dao;

    @Override
    public void init() throws ServletException {
        try {
            Context initCtx = new InitialContext();
            DataSource ds = (DataSource) initCtx.lookup("java:/comp/env/jdbc/mariadb");
            this.dao = new LibraryDao(ds);
        } catch (NamingException e) {
            throw new ServletException("JNDI Lookup failed", e);
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
        String codeStr = request.getParameter("code");

        if ("checkExemplar".equals(action)) {
            if (codeStr == null || codeStr.trim().isEmpty()) {
                out.print("{\"success\":false,\"message\":\"Code darf nicht leer sein\"}");
                return;
            }
            try {
                int code = Integer.parseInt(codeStr.trim());
                LibraryDao.ReturnInfo returnInfo = dao.checkExemplarForReturn(code);
                if (returnInfo != null) {
                    if (returnInfo.memberCode == null) {
                        out.print("{\"success\":false,\"message\":\"Dieses Exemplar ist zurzeit nicht ausgeliehen\"}");
                    } else {
                        out.print("{\"success\":true,\"code\":" + returnInfo.code +
                                  ",\"title\":\"" + JsonHelper.escape(returnInfo.title) +
                                  "\",\"author\":\"" + JsonHelper.escape(returnInfo.author) +
                                  "\",\"memberCode\":\"" + JsonHelper.escape(returnInfo.memberCode) +
                                  "\",\"memberName\":\"" + JsonHelper.escape(returnInfo.memberName) +
                                  "\",\"dueDate\":\"" + returnInfo.dueDate.toString() +
                                  "\",\"overdue\":" + returnInfo.isOverdue +
                                  ",\"timeDiffText\":\"" + JsonHelper.escape(returnInfo.timeDiffText) + "\"}");
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

        String exemplarCodesStr = request.getParameter("exemplarCodes");

        if (exemplarCodesStr == null || exemplarCodesStr.trim().isEmpty()) {
            out.print("{\"success\":false,\"message\":\"Fehlende Parameter\"}");
            return;
        }

        String[] exemplarCodes = exemplarCodesStr.split(",");
        try {
            LibraryDao.ReturnResult result = dao.executeReturnTransaction(exemplarCodes);

            // Push print job to Redis
            long timestamp = System.currentTimeMillis() / 1000;
            List<String> itemsDetailsJson = new ArrayList<>();
            for (LibraryDao.ReturnedBook book : result.books) {
                itemsDetailsJson.add(String.format(
                    "{\"code\":\"%s\",\"title\":\"%s\",\"author\":\"%s\",\"overdue\":%b,\"overdueDays\":%d}",
                    JsonHelper.escape(book.code), JsonHelper.escape(book.title), JsonHelper.escape(book.author),
                    book.overdue, book.overdueDays
                ));
            }
            String itemsList = String.join(",", itemsDetailsJson);
            String printCommandJson = String.format(
                "{\"commandType\":\"RETURN\",\"memberCode\":\"%s\",\"memberName\":\"%s\",\"timestamp\":%d,\"items\":[%s]}",
                JsonHelper.escape(result.memberCode != null ? result.memberCode : "U"),
                JsonHelper.escape(result.memberName != null ? result.memberName : "Unbekannt"),
                timestamp, itemsList
            );

            try {
                RedisClient redis = JedisAdapter.getClient();
                redis.lpush("print_queue", printCommandJson);
            } catch (Exception e) {
                System.err.println("Fehler beim Senden des Rückgabe-Druckauftrags: " + e.getMessage());
            }

            out.print("{\"success\":true,\"message\":\"Rückgabe erfolgreich durchgeführt\"}");

        } catch (Exception e) {
            out.print("{\"success\":false,\"message\":\"Fehler bei Rückgabe: " + JsonHelper.escape(e.getMessage()) + "\"}");
        }
    }
}
