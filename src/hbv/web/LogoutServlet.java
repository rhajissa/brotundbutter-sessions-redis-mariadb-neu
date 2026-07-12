package hbv.web;
import java.io.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;

public class LogoutServlet extends HttpServlet {

  protected void doGet(HttpServletRequest  request,
      HttpServletResponse response)
      throws IOException, ServletException {

      HttpSession session=request.getSession(false);
      if(session != null){
        // String name = (String)session.getAttribute("user");
        session.invalidate();
      }

      response.setContentType("text/plain");
      PrintWriter out = response.getWriter();
      out.println("you are logged out ");
  }
}
