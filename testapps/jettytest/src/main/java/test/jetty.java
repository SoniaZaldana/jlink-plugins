///usr/bin/env jbang "$0" "$@" ; exit $?

//DEPS org.eclipse.jetty:jetty-server:11.0.14,org.eclipse.jetty:jetty-servlet:11.0.14
//JAVA 9+
//MODULE jettytest

package test;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import java.io.IOException;

public class jetty {
    public static void main(String... args) throws Exception {
        var servlet = new HttpServlet() {
                protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
                    response.setStatus(HttpServletResponse.SC_OK);
                    response.setContentType("text/html");
                    response.setCharacterEncoding("utf-8");
                    response.getWriter().println("<h1>Hello from HelloServlet</h1>");
                }
        };

        var handler = new ServletHandler();
        handler.addServletWithMapping(new ServletHolder(servlet), "/*");

        var server = new Server(8080);
        server.setHandler(handler);

        server.start();
        server.dumpStdErr();
        server.join();
    }
}

