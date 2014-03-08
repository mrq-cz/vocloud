package cz.mrq.vocloud.ejb;

import cz.mrq.vocloud.entity.Job;
import cz.mrq.vocloud.entity.Phase;
import cz.mrq.vocloud.entity.UserAccount;
import cz.mrq.vocloud.managedbeans.RegisterBean;
import cz.mrq.vocloud.tools.Config;
import cz.mrq.vocloud.tools.Toolbox;
import org.apache.commons.io.FileUtils;

import javax.annotation.Resource;
import javax.ejb.Asynchronous;
import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.mail.*;
import javax.mail.internet.*;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.PersistenceException;
import javax.persistence.Query;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author voadmin
 */
@Stateless
public class JobFacade extends AbstractFacade<Job> {

    private static final Logger logger = Logger.getLogger(JobFacade.class.toString());
    
    @PersistenceContext(unitName = "vokorelPU")
    private EntityManager em;
    @EJB
    private SchedulerBean sb;
    @Resource(name = "vokorel-mail")
    private Session mailSession;
    @Inject
    @Config
    private String jobsDir;
    @Inject
    @Config
    private String examplesDir;
    @Inject
    @Config
    private String scriptsDir;

    @Override
    protected EntityManager getEntityManager() {
        return em;
    }

    public JobFacade() {
        super(Job.class);
    }

    public void start(Job job) throws IOException {
        job.start();
        sb.addWatchedJob(job);
        sb.updateExecutingJobs();
        edit(job);
    }

    public void abort(Job job) throws IOException {
        job.abort();
        sb.updateExecutingJobs();
        edit(job);
    }

    public File getFileDir(Job job) {
        return new File(jobsDir + "/" + job.getId());
    }

    /**
     * size of the job directory on disk
     *
     * @return size in bytes
     */
    public Long getSize(Job job) {
        File dir = getFileDir(job);
        if (dir != null && dir.exists()) {
            return FileUtils.sizeOfDirectory(dir);
        } else {
            return 0L;
        }
    }

    public void evictCache() {
        em.getEntityManagerFactory().getCache().evictAll();
        logger.info("clearing cache");
    }

    public List<Job> findByOwnerId(UserAccount owner) {
        Query q = getEntityManager().createNamedQuery("Job.findByOwnerId");
        q.setParameter("owner", owner);
        try {
            return q.getResultList();
        } catch (PersistenceException pe) {
            Logger.getGlobal().log(Level.WARNING, "query failed: {0}", pe.toString());
        }
        return null;
    }

    public List<Job> userJobList(UserAccount owner) {
        Query q = getEntityManager().createNamedQuery("Job.userJobList");
        q.setParameter("owner", owner);
        try {
            return q.getResultList();
        } catch (PersistenceException pe) {
            Logger.getGlobal().log(Level.WARNING, "query failed: {0}", pe.toString());
        }
        return null;
    }

    /**
     * downloads results of the job to the local storage
     *
     * @return success
     */
    public Boolean downloadResults(Job job) {
        if (job.getUwsJob().getResults() == null) {
            return false;
        }
        File results = new File(getFileDir(job), "results.zip");
        Boolean result;
        try {
            result = Toolbox.downloadFile(job.getUwsJob().getResultUrl(), results);
            Toolbox.decompress(results, results.getParentFile());
            logger.info("results for job " + job.getId() + " downloaded");
        } catch (Exception ex) {
            result = false;
            logger.log(Level.SEVERE, null, ex);
        }
        return result;
    }

    /**
     * size of the total disk space used by the useraccount
     *
     * @return bytes
     */
    public Long getSize(UserAccount user) {
        Long sum = 0L;
        for (Job j : userJobList(user)) {
            sum += getSize(j);
        }
        return sum;
    }

    public List<Job> findByPhase(Phase phase) {
        Query q = getEntityManager().createNamedQuery("Job.findByPhase");
        q.setParameter("phase", phase);
        try {
            return q.getResultList();
        } catch (PersistenceException pe) {
            Logger.getGlobal().log(Level.WARNING, "query failed: {0}", pe.toString());
        }
        return null;
    }

    public void delete(Job job) {
        // delete files
        File dir = getFileDir(job);
        if (dir != null) {
            Toolbox.delete(dir);
        }
        // destroy on uws
        job.destroyOnUWS();

        // remove form scheduler
        sb.removeWatchedJob(job);

        // remove job from database
        remove(job);
    }

    /**
     * sends results of the job to the email set in the job
     *
     * @param job
     */
    public void sendResults(Job job) {
        if (job.getResultsEmail() == null) {
            return;
        }
        Message message = new MimeMessage(mailSession);
        try {
            message.addRecipient(Message.RecipientType.TO, new InternetAddress(job.getResultsEmail()));
            message.setFrom();

            // 
            message.setSubject("vo-korel: Results of '" + job.getLabel() + "' job");

            //multipart
            Multipart mp = new MimeMultipart();

            // message body
            StringBuilder sb = new StringBuilder();
            sb.append("Your job '").append(job.getLabel()).append("' ");
            if (job.getPhase() == Phase.COMPLETED) {
                sb.append("finished successfully.");
            }
            if (job.getPhase() == Phase.ERROR) {
                sb.append("finished with error.");
            }
            if (job.getPhase() == Phase.ABORTED) {
                sb.append("has been aborted,");
            }
            sb.append("\n");
            sb.append("Complete results are in the attachment.");

            MimeBodyPart text = new MimeBodyPart();
            text.setText(sb.toString());

            mp.addBodyPart(text);

            //attachments
            File resultsFile = new File(getFileDir(job), "results.zip");

            if (resultsFile.exists()) {
                MimeBodyPart attachment = new MimeBodyPart();
                try {
                    attachment.attachFile(resultsFile);
                } catch (IOException ex) {
                    logger.log(Level.SEVERE, null, ex);
                }
                attachment.setFileName("results" + job.getStringId() + ".zip");
                mp.addBodyPart(attachment);
            }

            message.setContent(mp);
            message.setHeader("X-Mailer", "My Mailer");
            Transport.send(message);
            logger.log(Level.INFO, "Result were sent to {0}", job.getResultsEmail());
        } catch (AddressException ex) {
            logger.log(Level.SEVERE, null, ex);
        } catch (MessagingException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    /**
     * this method run asynchronously to start post process scripts for a job
     * 
     * requires java 7
     * 
     * @since 1.7 
     * @param job
     */
    @Asynchronous
    public void postProcess(Job job) {
        String jobScripts = scriptsDir+"/"+job.getJobType();
        logger.log(Level.INFO, "starting post-process of job, scripts folder: {0} ", jobScripts);
        File jobScriptsDir = new File(jobScripts);
        if (!jobScriptsDir.exists() || !jobScriptsDir.isDirectory() || jobScriptsDir.list().length < 1) {
            logger.log(Level.INFO, "skipping post process because there are no scripts");
            return;
        }
        
        job.setPhase(Phase.PROCESSING);
        
        File workingDir = getFileDir(job);
        

        // prepare post-process
        File postOutput = new File(workingDir, "post-scripts.out");
        try {
            postOutput.createNewFile();
        } catch (IOException ex) {
            logger.log(Level.SEVERE, null, ex);
            return;
        }
        Process postProcess;
        
        // run parts run any executable (must have permission) scripts *.sh in scripts directory
        ProcessBuilder postPB = new ProcessBuilder("run-parts", "-v", "--regex=", jobScripts);
        
        postPB.directory(workingDir);        
        
        try {
            postPB.redirectErrorStream(true);
            postPB.redirectOutput(postOutput);
            postProcess = postPB.start();
            postProcess.waitFor();
        } catch (Exception e) {
            logger.log(Level.SEVERE, null, e);
        }
        
        job.setPhase(Phase.COMPLETED);
        
        
        File results = new File(workingDir,"results.zip");
        Toolbox.delete(results);
        Toolbox.compressFiles(workingDir, results);
        
        
        sendResults(job);
               
    }

    public void prepareKorelJobExamples(UserAccount user) {
        if (examplesDir == null) {
            return;
        }

        File[] examples = new File(examplesDir).listFiles();
        
        if (examples == null) {
            logger.warning("No job examples copied!");
            return;
        }
        
        for (File example : examples) {
            File jobFile = new File(example, "job.properties");
            if (jobFile.exists()) {

                // create job according to properties file
                Properties jobDescription = new Properties();
                try {
                    jobDescription.load(new FileInputStream(jobFile));
                } catch (IOException ex) {
                    logger.log(Level.SEVERE, "failed to read example file desription " + example.getName(), ex);
                    continue;
                }

                Job exampleJob = new Job();

                exampleJob.setLabel(jobDescription.getProperty("label"));
                exampleJob.setNotes(jobDescription.getProperty("notes"));

                if (exampleJob.getLabel() == null) {
                    continue;
                }

                Date now = new Date();
                exampleJob.setJobType("Korel");
                exampleJob.setUws(null);
                exampleJob.setUwsId(null);
                exampleJob.setCreatedDate(now);
                exampleJob.setFinishedDate(now);
                exampleJob.setStartedDate(now);
                exampleJob.setOwner(user);
                exampleJob.setPhase(Phase.COMPLETED);

                this.create(exampleJob);

                // create job folder
                File jobFolder = this.getFileDir(exampleJob);
                jobFolder.mkdirs();

                // copy example files
                try {
                    FileUtils.copyDirectory(example, jobFolder);
                } catch (IOException ex) {
                    Logger.getLogger(RegisterBean.class.getName()).log(Level.SEVERE, "cannot copy example files for the job", ex);
                    this.delete(exampleJob);
                }

                // delete copied properties file
                FileUtils.deleteQuietly(new File(jobFolder, "job.properties"));
            }
        }
    }

    public void exportUWSJob(Job job) {
        File uwsFile = new File(getFileDir(job), "uws-job.xml");
        try {
            FileUtils.writeStringToFile(uwsFile, job.getUwsJobXml());
        } catch (IOException e) {
            logger.warning("failed to save uws-job.xml");
        }
    }
}
