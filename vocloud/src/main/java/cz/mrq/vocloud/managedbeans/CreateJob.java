package cz.mrq.vocloud.managedbeans;

import cz.mrq.vocloud.ejb.JobFacade;
import cz.mrq.vocloud.ejb.UWSFacade;
import cz.mrq.vocloud.ejb.UserSessionBean;
import cz.mrq.vocloud.entity.Job;
import cz.mrq.vocloud.entity.Phase;
import cz.mrq.vocloud.tools.Config;
import cz.mrq.vocloud.tools.Toolbox;
import cz.mrq.vocloud.uwsparser.model.UWSJob;
import cz.mrq.vocloud.uwsparser.UWSParserManager;
import org.apache.commons.io.FileUtils;
import org.primefaces.component.panel.Panel;
import org.primefaces.event.FileUploadEvent;
import org.primefaces.model.UploadedFile;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.ejb.EJB;
import javax.faces.application.FacesMessage;
import javax.faces.application.NavigationHandler;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.ViewScoped;
import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.inject.Inject;
import java.io.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 *
 * @author voadmin
 */
@ManagedBean(name = "createJob")
@ViewScoped
public class CreateJob implements Serializable {

    private static final Logger logger = Logger.getLogger(CreateJob.class.getName());

    @EJB private JobFacade jf;
    @EJB private UWSFacade uws;
    @EJB private UserSessionBean usb;

    @Inject @Config
    private String applicationAddress;
    @Inject @Config
    private String tempDir;
    @Inject @Config
    private String jobsDir;

    private Job parent;
    private String parFileContents;
    private Job job;
    private UUID tid;
    private Boolean run = false;
    private Boolean email = false;
    private Boolean par = false;
    private Boolean dat = false;
    private Boolean tmp = false;
    private File uploadDir;
    private List<File> uploadedFiles;
    private Panel editParPanel = new Panel();

    public CreateJob() {
        job = new Job();
        tid = UUID.randomUUID();
        job.setJobType("Korel");
        uploadedFiles = new ArrayList<>();
        uploadDir = new File(tempDir, tid.toString());
    }

    @PostConstruct
    public void init() {
        editParPanel.setCollapsed(true);

        // if we are creating from other job, get parent
        parent = (Job) FacesContext.getCurrentInstance().getAttributes().get("parent");

        // copy values from parent
        if (parent != null) {
            job.setLabel(parent.getLabel() + " (copy)");
            job.setNotes(parent.getNotes());

            // add parameters
            File dir = jf.getFileDir(parent);

            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (checkFileName(f.getName())) {
                        uploadedFiles.add(f);
                    }
                }
            }

            // read par file contents
            if (par) {
                setParFileContents(new File(dir, "korel.par"));
            }


        }
    }

    @PreDestroy
    public void deleteTempFiles() {
        if (uploadDir.exists()) {
            Toolbox.delete(uploadDir);
        }
    }

    public void handleFileUpload(FileUploadEvent event) {
        UploadedFile uploaded = event.getFile();
        String fileName = uploaded.getFileName();
        checkFileName(fileName);

        uploadDir.mkdirs();
        File outFile = new File(uploadDir, uploaded.getFileName());

        copyUploadedFile(outFile, uploaded);

        //extract uploaded zip file
        if (fileName.endsWith(".zip")) {
            try {
                ZipEntry entry;
                ZipFile zf = new ZipFile(outFile);
                Enumeration e = zf.entries();
                while (e.hasMoreElements()) {
                    entry = (ZipEntry) e.nextElement();

                    // flatten directories
                    String filename = entry.getName().substring(entry.getName().lastIndexOf('/') + 1);

                    if (checkFileName(filename)) {

                        File exFile = new File(uploadDir, filename);
                        copyFile(exFile, zf.getInputStream(entry));
                        uploadedFiles.add(exFile);
                    }
                }
            } catch (ZipException ex) {
                logger.log(Level.SEVERE, "zip exception");
                FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, "Not a valid zip file.", null));
            } catch (IOException ex) {
                logger.log(Level.SEVERE, null, ex);
            }
        } else {
            uploadedFiles.add(outFile);
        }

        // read par file contents
        if (par) {
            setParFileContents(new File(uploadDir, "korel.par"));
        }

        logger.info("File uploaded: " + event.getFile().getFileName());
    }

    protected Boolean checkFileName(String fileName) {
        if ("korel.par".equals(fileName)) {
            par = true;
            return true;
        }
        if ("korel.dat".equals(fileName)) {
            dat = true;
            return true;
        }
        if ("korel.tmp".equals(fileName)) {
            tmp = true;
            return true;
        }
        return false;
    }

    protected void copyUploadedFile(File out, UploadedFile uf) {
        try {
            copyFile(out, uf.getInputstream());
        } catch (IOException ex) {
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, "File copy failed.", ex.toString()));
            logger.log(Level.SEVERE, null, ex);
        }
    }

    protected void copyFile(File out, InputStream is) {
        try {
            BufferedInputStream bis = new BufferedInputStream(is);
            FileUtils.copyInputStreamToFile(bis, out);
        } catch (IOException ex) {
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, "File copy failed.", ex.toString()));
            logger.log(Level.SEVERE, null, ex);
        }
    }

    /**
     * creates job
     */
    public void save() {
        FacesContext currentInstance = FacesContext.getCurrentInstance();

        // check for free space
        if (usb.getUser().getQuota() <= jf.getSize(usb.getUser())) {
            logger.log(Level.SEVERE, "Job cant be created not enough user space");
            currentInstance.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, "Job could been created, you exceeded your disk quota.", "Please free some space first."));
        }

        // set owner and save to the database to get id
        job.setOwner(usb.getUser());
        job.setId(0L);
        job.setCreatedDate(new Date());
        jf.create(job);

        // results email
        if (email) {
            job.setResultsEmail(usb.getUser().getEmail());
        }

        // create job folder and copy uploaded files there 
        File jobFolder = new File(jobsDir, job.getId().toString());
        jobFolder.mkdirs();
        for (File f : uploadedFiles) {
            try {
                if (f.exists()) {
                    FileUtils.copyFileToDirectory(f, jobFolder);
                }
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "cannot copy file", ex);
                //currentInstance.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, "Job could been created (IO error)", ex.toString()));
            }
        }

        //replace korel.par by data from textfield
        try {
            FileUtils.writeStringToFile(new File(jobFolder, "korel.par"), getParFileContents());
        } catch (IOException ex) {
            logger.log(Level.SEVERE, null, ex);
        }

        deleteTempFiles();

        // pack files to param file
        File parameters = new File(jobFolder, "parameters.zip");
        Toolbox.compressFiles(jobFolder, parameters);

        // expose for download
        String exposeLocal = currentInstance.getExternalContext().getRealPath("/download/" + tid);
        File expose = new File(exposeLocal);
        expose.mkdirs();
        try {
            FileUtils.copyFileToDirectory(parameters, expose);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, null, ex);
        }

        String link = applicationAddress + "/download/" + tid + "/" + parameters.getName();

        // find uws and create job there
        job.setUws(uws.assign("Korel"));
        
        if (job.getUws() == null) {
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR,
                    "Can't create job!", "No worker is available."));
            jf.delete(job);
            return;
        }

        String param = "zip=" + link;
        String reply;
        try {
            reply = job.getUws().createJob(param);
        } catch (IOException ex) {
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR,
                    "Can't create job!", "Korel worker is unavailable."));
            logger.log(Level.SEVERE, "cant create a job", ex);
            jf.delete(job);
            return;
        }

        // link up Job with UWSjob
        UWSJob uwsJob = UWSParserManager.getInstance().parseJob(reply);
        if (uwsJob == null || uwsJob.getJobId() == null) {
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR,
                    "Failed to create a job on worker '" + job.getUws().getLabel() + "' !",
                    "Worker is probably not configured properly."));
            logger.log(Level.SEVERE, "can't create job");
            job.setPhase(Phase.ERROR);
        }
        job.setUwsJob(uwsJob);
        job.updateFromUWSJob();

        jf.edit(job);

        // execute if wanted
        if (run) {
            try {
                jf.start(job);
            } catch (IOException ex) {
                FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR,
                        "Failed to start a job on worker '" + job.getUws().getLabel() + "' !", ex.getMessage()));
                logger.log(Level.SEVERE, ex.getMessage(), ex);
            }
        }

        // refresh jobs bean (if exist)
        JobsBean jb = (JobsBean) currentInstance.getExternalContext().getSessionMap().get("jobs");
        if (jb != null) {
            jb.refresh();
        }

        //navigate away


        //currentInstance.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_INFO, "Job created.", "New job '" + job.getLabel() + "' created."));

        NavigationHandler myNav = currentInstance.getApplication().getNavigationHandler();
        myNav.handleNavigation(currentInstance, "create", "index");
    }

    public void saveRun() {
        run = true;
        save();
    }

    public void cancel() {
        FacesContext currentInstance = FacesContext.getCurrentInstance();
        NavigationHandler myNav = currentInstance.getApplication().getNavigationHandler();
        myNav.handleNavigation(currentInstance, "create", "index");
    }

    public String getParFileContents() {
        if (parFileContents == null) {
            if (parent != null) {
                setParFileContents(new File(jf.getFileDir(parent), "korel.par"));
            }
        }
        return parFileContents;
    }

    public Job getJob() {
        return job;
    }

    public void setJob(Job job) {
        this.job = job;
    }

    public Boolean getEmail() {
        return email;
    }

    public void setEmail(Boolean email) {
        this.email = email;
    }

    public Boolean getRun() {
        return run;
    }

    public void setRun(Boolean run) {
        this.run = run;
    }

    public List<File> getUploadedFiles() {
        return uploadedFiles;
    }

    public Boolean getDat() {
        return dat;
    }

    public void setDat(Boolean dat) {
        this.dat = dat;
    }

    public Boolean getPar() {
        return par;
    }

    public void setPar(Boolean par) {
        this.par = par;
    }

    public Boolean getTmp() {
        return tmp;
    }

    public void setTmp(Boolean tmp) {
        this.tmp = tmp;
    }

    @Override
    public String toString() {
        return "CreateJob{" + "tid=" + tid.toString() + "job=" + job + ", run=" + run + ", email=" + email + ", uploadedFiles(" + uploadedFiles.size() + ")=" + uploadedFiles + '}';
    }

    public void setParFileContents(String contents) {
        editParPanel.setCollapsed(false);
        parFileContents = contents;
    }

    private void setParFileContents(File file) {
        try {
            parFileContents = FileUtils.readFileToString(file);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, null, ex);
            return;
        }
        editParPanel.setCollapsed(false);
    }

    public Panel getEditParPanel() {
        return editParPanel;
    }

    public void setEditParPanel(Panel editParPanel) {
        this.editParPanel = editParPanel;
    }
}
