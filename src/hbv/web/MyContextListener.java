package hbv.web;

import jakarta.servlet.*;

public class MyContextListener implements ServletContextListener, ServletRequestListener {
  ServletContext ctx;

  public void contextInitialized(ServletContextEvent servletContextEvent) {
    ctx = servletContextEvent.getServletContext();
    String redis_password = ctx.getInitParameter("redispassword");
    String redis_server = ctx.getInitParameter("redisserver");
    JedisAdapter.init(redis_server,6379,redis_password,500);
    ctx.log("contextInitialized");
  }

  public void contextDestroyed(ServletContextEvent servletContextEvent) {
    JedisAdapter.destroy();
    ctx.log("contextDestroyed");
  }
}

