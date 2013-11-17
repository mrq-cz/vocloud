package cz.mrq.vocloud.managedbeans;

import cz.mrq.vocloud.ejb.JobFacade;
import cz.mrq.vocloud.ejb.UserSessionBean;
import cz.mrq.vocloud.entity.Job;
import cz.mrq.vocloud.entity.UserAccount;
import cz.mrq.vocloud.tools.Toolbox;
import cz.mrq.vocloud.uwsparser.UWSJobPhase;
import org.apache.commons.io.FileUtils;
import org.primefaces.component.poll.Poll;

import javax.annotation.PostConstruct;
import javax.ejb.EJB;
import javax.faces.application.FacesMessage;
import javax.faces.application.NavigationHandler;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.SessionScoped;
import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.faces.event.ActionEvent;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author voadmin
 */
@ManagedBean(name = "jobs")
@SessionScoped
public class JobsBean implements Serializable {

    @EJB
    private JobFacade jobFacade;
    @EJB
    private UserSessionBean usb;
    private List<Job> jobs;
    private UserAccount user;
    private Job selected;
    private File selectedFile;
    private Poll detailsPoll = new Poll();

    public JobsBean() {
        Logger.getLogger(JobsBean.class.getName()).info("Creating JobsBean");
    }

    @PostConstruct
    public void refresh() {
        user = usb.getUser();
        jobs = jobFacade.userJobList(user);
    }

    public void runAgain(ActionEvent e) {
        NavigationHandler myNav = FacesContext.getCurrentInstance().getApplication().getNavigationHandler();
        FacesContext.getCurrentInstance().getAttributes().put("parent", selected);

        myNav.handleNavigation(FacesContext.getCurrentInstance(), "details", "create");
    }

    public void details(ActionEvent e) {
        selected = (Job) e.getComponent().getAttributes().get("selectedJob");
        selectedFile = null;
        detailsPollListener();

        // navigate 
        NavigationHandler myNav = FacesContext.getCurrentInstance().getApplication().getNavigationHandler();
        myNav.handleNavigation(FacesContext.getCurrentInstance(), "index", "details");
    }

    public void start(ActionEvent e) {
        selected = (Job) e.getComponent().getAttributes().get("selectedJob");
        try {
            jobFacade.start(selected);
        } catch (Exception ex) {
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, "Cant start a job!", ex.toString()));
            Logger.getLogger(JobsBean.class.getName()).log(Level.SEVERE, null, ex);
        }
        this.refresh();
    }

    public void abort(ActionEvent e) {
        selected = (Job) e.getComponent().getAttributes().get("selectedJob");
        try {
            jobFacade.abort(selected);
        } catch (Exception ex) {
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, "Cant abort a job!", ex.toString()));
            Logger.getLogger(JobsBean.class.getName()).log(Level.SEVERE, null, ex);
        }
        this.refresh();
    }

    public void delete(ActionEvent e) {
        if (selected == null) {
            selected = (Job) e.getComponent().getAttributes().get("selectedJob");
        }
        String name = selected.getLabel();
        jobFacade.delete(selected);

        this.refresh();
        FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_INFO,"Job \""+name+"\" has been deleted."," "));
    }

    public void deleteSelected(ActionEvent e) {
        if (selected == null) {
            return;
        }
        jobFacade.delete(selected);


        this.refresh();
        // navigate back to joblist
        NavigationHandler myNav = FacesContext.getCurrentInstance().getApplication().getNavigationHandler();
        myNav.handleNavigation(FacesContext.getCurrentInstance(), "details", "index");
    }
    
    public void detailsPollListener() {
        selected = jobFacade.find(selected.getId());
        
        // stop pooling if job isnt in progress
        if (selected.getPhase() != UWSJobPhase.EXECUTING & selected.getPhase() != UWSJobPhase.QUEUED)
            detailsPoll.setStop(true);
        else
            detailsPoll.setStop(false);
    }

    public void download(ActionEvent e) throws IOException {
        File file = (File) e.getComponent().getAttributes().get("selectedFile");
        Logger.getLogger(JobsBean.class.getName()).info("Downloading " + file.getAbsolutePath());
        FacesContext facesContext = FacesContext.getCurrentInstance();
        ExternalContext externalContext = facesContext.getExternalContext();
        HttpServletResponse response = (HttpServletResponse) externalContext.getResponse();

        response.reset();
        response.setContentType(externalContext.getMimeType(file.getName()));
        response.setHeader("Content-disposition", "attachment; filename=\"" + file.getName() + "\"");

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

        facesContext.responseComplete();
    }

    public Boolean isImage() {
        FacesContext currentInstance = FacesContext.getCurrentInstance();
        ExternalContext externalContext = currentInstance.getExternalContext();
        File file = (File) externalContext.getRequestMap().get("file");
        return file.getName().endsWith("png");

    }

    /**
     * get files of slected job (without 0 length files)
     *
     * @return
     */
    public List<File> getFiles() {
        File[] listFiles = jobFacade.getFileDir(selected).listFiles();
        List<File> files = new ArrayList<File>();
        if (listFiles != null) {
            for (File f : listFiles) {
                if (f.length() > 1 & !f.getName().endsWith("png")) {
                    files.add(f);
                }
            }
        }
        return files;
    }

    public List<File> getImages() {
        File[] listFiles = jobFacade.getFileDir(selected).listFiles();
        List<File> images = new ArrayList<File>();
        if (listFiles != null) {
            for (File file : listFiles) {
                if (file.getName().endsWith("png") & file.length() != 0) {
                    images.add(file);
                }
            }
        }
        Collections.sort(images);
        return images;
    }

    public List<Job> getJobs() {
        return jobs;
    }

    public Job getSelected() {
        return selected;
    }

    public void setSelected(Job selected) {
        this.selected = selected;
    }

    public File getSelectedFile() {
        return selectedFile;
    }

    public String getSelectedFileContents() {
        if (selectedFile == null) {
            return "(no file selected)";
        }
        if (selectedFile.getName().endsWith("png")) {
            return "(image)";
        }
        if (selectedFile.getName().endsWith("zip")) {
            return Toolbox.ziplist(selectedFile);
        }
        String content = "";
        try {
            content = FileUtils.readFileToString(selectedFile);
        } catch (IOException ex) {
            Logger.getLogger(JobsBean.class.getName()).log(Level.SEVERE, null, ex);
        }
        return content;
    }

    public void setSelectedFile(File selectedFile) {
        this.selectedFile = selectedFile;
    }

    public Long getUsedSize() {
        if (jobFacade == null) {
            return 0L;
        }
        return jobFacade.getSize(user);
    }

    public int getUsedPercent() {
        if (user == null) {
            return 0;
        }
        Long used = getUsedSize();
        if (used == null) {
            return 0;
        }
        int percent = (int) (((float) used / (float) user.getQuota()) * 100);
        //if(percent > 100) percent = 100;
        return percent;
    }

    public String getSpaceUsage() {
        return Toolbox.humanReadableByteCount(getUsedSize(), true) + " / " + Toolbox.humanReadableByteCount(user.getQuota(), true);
    }

    public Poll getDetailsPoll() {
        return detailsPoll;
    }

    public void setDetailsPoll(Poll detailsPoll) {
        this.detailsPoll = detailsPoll;
    }
    
    
}
