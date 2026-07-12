package hbv.web;

import java.io.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;

public class SessionStatusServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();

        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute("user") != null) {
            String username = (String) session.getAttribute("user");
            out.print("{\"loggedIn\":true,\"user\":\"" + JsonHelper.escape(username) + "\"}");
        } else {
            out.print("{\"loggedIn\":false}");
        }
    }
}
