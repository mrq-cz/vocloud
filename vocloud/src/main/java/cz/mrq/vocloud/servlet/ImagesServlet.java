package cz.mrq.vocloud.servlet;

import cz.mrq.vocloud.ejb.JobFacade;
import cz.mrq.vocloud.entity.Job;
import cz.mrq.vocloud.tools.Config;

import javax.ejb.EJB;
import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;

/**
 *
 * @author voadmin
 */
@WebServlet(name = "ImagesServlet", urlPatterns = {"/jobs/images/*"})
public class ImagesServlet extends HttpServlet {
    
    @EJB
    JobFacade jf;

    @Inject
    @Config
    private String jobsDir;
    
    /**
     * Processes requests for both HTTP
     * <code>GET</code> and
     * <code>POST</code> methods.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     * 
     * 
     */
    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        
        String path = request.getPathInfo();
        String[] split = path.split("/");
        String jobId = split[1];
        String fileName = split[2];
       
        //only owner can acces image
        //TODO fix bugs
        Job job = jf.find(Long.parseLong(jobId));
        String user = request.getRemoteUser();
        if (!job.getOwner().getUsername().equals(user)) {
            response.sendError(403);
            return;
        }
        
        if (!fileName.endsWith("png")) return;
        
        //Logger.getLogger(ImagesServlet.class.toString()).log(Level.INFO, "Serving image {0} from job {1}", new Object[]{fileName, jobId});
        
        response.setContentType("text/html;charset=UTF-8");
        
        File file = new File(jobsDir+path);
        
        //Logger.getLogger(JobsBean.class.getName()).info("Generating image "+file.getAbsolutePath());

        if (!file.exists()) return;
        
        response.reset(); // Some JSF component library or some Filter might have set some headers in the buffer beforehand. We want to get rid of them, else it may collide.
        response.setContentType("image/png"); // Check http://www.w3schools.com/media/media_mimeref.asp for all types. Use if necessary ServletContext#getMimeType() for auto-detection based on filename.
        response.setHeader("Content-disposition", "inline; filename=\""+file.getName()+"\""); // The Save As popup magic is done here. You can give it any filename you want, this only won't work in MSIE, it will use current request URL as filename instead.

        BufferedInputStream input = null;
        BufferedOutputStream output = null;

        try {
            input = new BufferedInputStream(new FileInputStream(file));
            output = new BufferedOutputStream(response.getOutputStream());

            byte[] buffer = new byte[10240];
            for (int length; (length = input.read(buffer)) > 0;) {
                output.write(buffer, 0, length);
            }
            
        } finally {
            output.close();
            input.close();
        }
        
    }
    // <editor-fold defaultstate="collapsed" desc="HttpServlet methods. Click on the + sign on the left to edit the code.">
    /**
     * Handles the HTTP
     * <code>GET</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /**
     * Handles the HTTP
     * <code>POST</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /**
     * Returns a short description of the servlet.
     *
     * @return a String containing servlet description
     */
    @Override
    public String getServletInfo() {
        return "Short description";
    }// </editor-fold>
}
