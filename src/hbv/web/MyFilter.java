package hbv.web;

import jakarta.servlet.*;
import jakarta.servlet.http.*;

public class MyFilter extends HttpFilter {
  ServletContext ctx;

  public void init(FilterConfig config) throws ServletException {
    ctx = config.getServletContext();
  }

  public void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws java.io.IOException, ServletException {

    String path = request.getRequestURI();
    String contextPath = request.getContextPath();
    String relativePath = path.substring(contextPath.length());

    // Public pages/endpoints that do not require an active session
    boolean isPublic = relativePath.equals("/") ||
                       relativePath.equals("/index.html") ||
                       relativePath.equals("/login.html") ||
                       relativePath.equals("/login") ||
                       relativePath.equals("/logout") ||
                       relativePath.equals("/session-status") ||
                       relativePath.equals("/default.txt");

    if (!isPublic) {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            // For HTML page requests, redirect to login page
            if (relativePath.endsWith(".html") || relativePath.equals("/dashboard") || relativePath.equals("/receipt")) {
                response.sendRedirect(contextPath + "/login.html");
            } else {
                // For API / Servlet calls, return 401 Unauthorized JSON
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                response.getWriter().print("{\"success\":false,\"message\":\"Nicht autorisiert\"}");
            }
            return;
        }
    }

    chain.doFilter(request, response);
  }

  public void destroy() {}
}
