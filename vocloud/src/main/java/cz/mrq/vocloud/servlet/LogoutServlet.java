package cz.mrq.vocloud.servlet;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

/**
 *
 * @author voadmin
 */
@WebServlet(name = "LogoutServlet", urlPatterns = {"/logout.xhtml"})
public class LogoutServlet extends HttpServlet {

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // Destroys the session for this user.
        HttpSession session;

        session = request.getSession(false);

        if (session != null) {
            session.invalidate();
        }

        // Redirects back to the initial page.
        response.sendRedirect(request.getContextPath());
    }
}
