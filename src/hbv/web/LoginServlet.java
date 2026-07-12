package hbv.web;
import java.io.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;

public class LoginServlet extends HttpServlet {

  protected void doPost(HttpServletRequest request, HttpServletResponse response)
      throws IOException, ServletException {
      doGet(request, response);
  }

  protected void doGet(HttpServletRequest  request,
      HttpServletResponse response)
      throws IOException, ServletException {

      String user=request.getParameter("user");
      String passwd=request.getParameter("passwd");

      // check if valid combination
      if (user != null && !user.trim().isEmpty() && passwd != null && !passwd.trim().isEmpty()){
        // prevent session fixation
        HttpSession oldSession = request.getSession(false);
        if (oldSession != null) {
          oldSession.invalidate();
        }

        HttpSession session=request.getSession();
        session.setAttribute("user",user);

        response.sendRedirect("protected");
      } else {
        response.sendRedirect("login.html?error=true");
      }
  }
}

