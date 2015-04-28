package cz.mrq.vocloud.ejb;

import cz.mrq.vocloud.entity.Job;
import cz.mrq.vocloud.entity.Phase;
import cz.mrq.vocloud.entity.UWS;
import cz.mrq.vocloud.entity.UWSType;
import cz.mrq.vocloud.entity.UserAccount;
import cz.mrq.vocloud.entity.Worker;
import cz.mrq.vocloud.tools.Config;
import cz.mrq.vocloud.tools.Toolbox;
import cz.mrq.vocloud.uwsparser.UWSParserManager;
import cz.mrq.vocloud.uwsparser.model.Result;
import cz.mrq.vocloud.uwsparser.model.UWSJob;
import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;

import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.mail.Session;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.PersistenceException;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.ejb.EJBException;
import javax.persistence.TypedQuery;
import org.zeroturnaround.zip.ZipUtil;

/**
 *
 * @author voadmin
 */
@Stateless
public class JobFacade extends AbstractFacade<Job> {

    private static final Logger LOG = Logger.getLogger(JobFacade.class.toString());

    @PersistenceContext(unitName = "vokorelPU")
    private EntityManager em;
    @EJB
    private UserSessionBean usb;
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

    public List<Job> getUserJobList(UserAccount owner) {
        TypedQuery<Job> q = getEntityManager().createNamedQuery("Job.userJobList", Job.class);
        q.setParameter("owner", owner);
        try {
            return q.getResultList();
        } catch (PersistenceException pe) {
            LOG.log(Level.WARNING, "query failed: {0}", pe.toString());
        }
        return null;
    }

    public void enqueueNewJob(Job job, boolean runImmediately) {
        //set create date
        job.setCreatedDate(new Date());
        //set user account
        job.setOwner(usb.getUser());
        //set phase
        job.setPhase(Phase.QUEUED);
        //find best uws for this job's uws type
        UWS bestUWS = findBestUwsJob(job.getUwsType());
        if (bestUWS == null) {
            //no possible uws for specified uws type
            throw new EJBException("No possible UWS for UWS type " + job.getUwsType().getStringIdentifier());
        }
        //assign bestUWS
        job.setUws(bestUWS);
        //persist new job to dabase
        create(job);
        //invoke asynchronous function to push job into specified uws
        sb.asyncPushJobToWorker(job, runImmediately);
    }

    protected UWS findBestUwsJob(UWSType uwsType) {
        TypedQuery<Worker> query = em.createNamedQuery("Worker.findWorkersWithUwsType", Worker.class);
        query.setParameter("uwsType", uwsType);
        List<Worker> possibleWorkers;
        try {
            possibleWorkers = query.getResultList();
        } catch (Exception ex) {
            LOG.log(Level.SEVERE, null, ex);
            return null;
        }
        if (possibleWorkers.isEmpty()) {
            return null;
        }
        Worker bestWorker = null;
        Long bestExecutingJobsCount = null;
        TypedQuery<Long> countQuery;
        for (Worker i : possibleWorkers) {
            countQuery = em.createNamedQuery("Worker.countWorkerJobsInPhase", Long.class);
            countQuery.setParameter("worker", i);
            countQuery.setParameter("phase", Phase.EXECUTING);
            Long count = countQuery.getSingleResult();
            if (bestWorker == null) {
                bestWorker = i;
                bestExecutingJobsCount = count;
                continue;
            }
            if (i.getMaxJobs() - count > bestWorker.getMaxJobs() - bestExecutingJobsCount) {
                bestWorker = i;
                bestExecutingJobsCount = count;
            }
        }
        //just to be sure
        if (bestWorker == null) {
            return null;//should not happen
        }
        //check if there are really free resources for the job
        if (bestWorker.getMaxJobs() - bestExecutingJobsCount == 0) {
            //use worker randomly (all are full)
            bestWorker = possibleWorkers.get((int) (Math.random() * possibleWorkers.size()));
        }

        //find specifis uws from worker
        for (UWS uws : bestWorker.getUwsList()) {
            if (uws.getUwsType().equals(uwsType)) {
                return uws;
            }
        }
        return null;//should not happen
    }

    public List<Job> findByPhase(Phase phase) {
        TypedQuery<Job> q = getEntityManager().createNamedQuery("Job.findByPhase", Job.class);
        q.setParameter("phase", phase);
        return q.getResultList();
    }

    public File getFileDir(Job job) {
        File result = new File(jobsDir + "/" + job.getId());
        result.mkdirs();
        return result;
    }
    /**
     * downloads results of the job to the local storage
     *
     * @param job
     * @param uwsJob
     * @return success
     */
    public boolean downloadResults(Job job, UWSJob uwsJob) {
        if (job == null){
            throw new IllegalArgumentException("Null job passed as argument");
        }
        if (uwsJob == null){
            throw new IllegalArgumentException("Null uwsJob passed as argument");
        }
        //check that there are some results
        if (uwsJob.getResults() == null || uwsJob.getResults().isEmpty()){
            return false;//no results
        }
        File results;
        boolean resFlag = true;
        try {
            for (Result r: uwsJob.getResults()){
                if (r.getHrefTrimmed() == null){
                    continue;
                }
                //else download
                String[] split = r.getHrefTrimmed().split("/");
                results = new File(getFileDir(job), split[split.length - 1]);
                if (!Toolbox.downloadFile(r.getHrefTrimmed(), results)){
                    resFlag = false;
                }
                if (results.getName().endsWith("zip")){
                    ZipUtil.unpack(results, results.getParentFile());
                }
                LOG.log(Level.INFO, "Result {0} for job {1} downloaded", new Object[]{results.getName(), job.getId()});
            }            
        } catch (Exception ex) {
            resFlag = false;
            LOG.log(Level.SEVERE, "Download failed", ex);
        }
        return resFlag;
    }
    
    public List<Job> findUserJobsPaginated(UserAccount userAcc, int first, int count){
        TypedQuery<Job> query = em.createNamedQuery("Job.userJobList", Job.class);
        query.setParameter("owner", userAcc);
        query.setFirstResult(first);
        query.setMaxResults(count);
        return query.getResultList();
    }
    
    public Long countUserJobs(UserAccount userAcc){
        TypedQuery<Long> query = em.createNamedQuery("Job.countUserJobs", Long.class);
        query.setParameter("owner", userAcc);
        return query.getSingleResult();
    }
//    public void start(Job job) throws IOException {
//        job.start();
//        sb.addWatchedJob(job);
//        sb.updateExecutingJobs();
//        edit(job);
//    }
//
//    public void abort(Job job) throws IOException {
//        job.abort();
//        sb.updateExecutingJobs();
//        edit(job);
//    }
//
//
//    /**
//     * size of the job directory on disk
//     *
//     * @return size in bytes
//     */
//    public Long getSize(Job job) {
//        File dir = getFileDir(job);
//        if (dir != null && dir.exists()) {
//            return FileUtils.sizeOfDirectory(dir);
//        } else {
//            return 0L;
//        }
//    }
//
//    public void evictCache() {
//        em.getEntityManagerFactory().getCache().evictAll();
//        logger.info("clearing cache");
//    }
//
//    public List<Job> findByOwnerId(UserAccount owner) {
//        Query q = getEntityManager().createNamedQuery("Job.findByOwnerId");
//        q.setParameter("owner", owner);
//        try {
//            return q.getResultList();
//        } catch (PersistenceException pe) {
//            logger.log(Level.WARNING, "query failed: {0}", pe.toString());
//        }
//        return null;
//    }
//
//
//
//    /**
//     * size of the total disk space used by the useraccount
//     *
//     * @return bytes
//     */
//    public Long getSize(UserAccount user) {
//        Long sum = 0L;
//        for (Job j : userJobList(user)) {
//            sum += getSize(j);
//        }
//        return sum;
//    }
//
//
//    public void delete(Job job) {
//        // delete files
//        File dir = getFileDir(job);
//        if (dir != null) {
//            Toolbox.delete(dir);
//        }
//        // destroy on uws
//        job.destroyOnUWS();
//
//        // remove form scheduler
//        sb.removeWatchedJob(job);
//
//        // remove job from database
//        remove(job);
//    }
//
//    /**
//     * sends results of the job to the email set in the job
//     *
//     * @param job
//     */
//    public void sendResults(Job job) {
//        if (job.getResultsEmail() == null) {
//            return;
//        }
//        Message message = new MimeMessage(mailSession);
//        try {
//            message.addRecipient(Message.RecipientType.TO, new InternetAddress(job.getResultsEmail()));
//            message.setFrom();
//
//            // 
//            message.setSubject("vo-korel: Results of '" + job.getLabel() + "' job");
//
//            //multipart
//            Multipart mp = new MimeMultipart();
//
//            // message body
//            StringBuilder sb = new StringBuilder();
//            sb.append("Your job '").append(job.getLabel()).append("' ");
//            if (job.getPhase() == Phase.COMPLETED) {
//                sb.append("finished successfully.");
//            }
//            if (job.getPhase() == Phase.ERROR) {
//                sb.append("finished with error.");
//            }
//            if (job.getPhase() == Phase.ABORTED) {
//                sb.append("has been aborted,");
//            }
//            sb.append("\n");
//            sb.append("Complete results are in the attachment.");
//
//            MimeBodyPart text = new MimeBodyPart();
//            text.setText(sb.toString());
//
//            mp.addBodyPart(text);
//
//            //attachments
//            File resultsFile = new File(getFileDir(job), "results.zip");
//
//            if (resultsFile.exists()) {
//                MimeBodyPart attachment = new MimeBodyPart();
//                try {
//                    attachment.attachFile(resultsFile);
//                } catch (IOException ex) {
//                    logger.log(Level.SEVERE, null, ex);
//                }
//                attachment.setFileName("results" + job.getStringId() + ".zip");
//                mp.addBodyPart(attachment);
//            }
//
//            message.setContent(mp);
//            message.setHeader("X-Mailer", "My Mailer");
//            Transport.send(message);
//            logger.log(Level.INFO, "Result were sent to {0}", job.getResultsEmail());
//        } catch (Exception ex) {
//            logger.log(Level.SEVERE, "failed to send email with job results", ex);
//        }
//    }
//
//    /**
//     * this method run asynchronously to start post process scripts for a job
//     *
//     * requires java 7
//     *
//     * @since 1.7
//     * @param job
//     */
//    @Asynchronous
//    public void postProcess(Job job) {
//        String jobScripts = scriptsDir + "/" + job.getJobType();
//        logger.log(Level.INFO, "starting post-process of job, scripts folder: {0} ", jobScripts);
//        File jobScriptsDir = new File(jobScripts);
//        if (!jobScriptsDir.exists() || !jobScriptsDir.isDirectory() || jobScriptsDir.list().length < 1) {
//            logger.log(Level.INFO, "skipping post process because there are no scripts");
//            return;
//        }
//
//        job.setPhase(Phase.PROCESSING);
//
//        File workingDir = getFileDir(job);
//
//        // prepare post-process
//        File postOutput = new File(workingDir, "post-scripts.out");
//        try {
//            postOutput.createNewFile();
//        } catch (IOException ex) {
//            logger.log(Level.SEVERE, null, ex);
//            return;
//        }
//        Process postProcess;
//
//        // run parts run any executable (must have permission) scripts *.sh in scripts directory
//        ProcessBuilder postPB = new ProcessBuilder("run-parts", "-v", "--regex=", jobScripts);
//
//        postPB.directory(workingDir);
//
//        try {
//            postPB.redirectErrorStream(true);
//            postPB.redirectOutput(postOutput);
//            postProcess = postPB.start();
//            postProcess.waitFor();
//        } catch (Exception e) {
//            logger.log(Level.SEVERE, null, e);
//        }
//
//        job.setPhase(Phase.COMPLETED);
//
//        File results = new File(workingDir, "results.zip");
//        Toolbox.delete(results);
//        Toolbox.compressFiles(workingDir, results);
//
//        sendResults(job);
//
//    }
//
//    public void prepareKorelJobExamples(UserAccount user) {
//        if (examplesDir == null) {
//            return;
//        }
//
//        File[] examples = new File(examplesDir).listFiles();
//
//        if (examples == null) {
//            logger.warning("No job examples copied!");
//            return;
//        }
//
//        for (File example : examples) {
//            File jobFile = new File(example, "job.properties");
//            if (jobFile.exists()) {
//
//                // create job according to properties file
//                Properties jobDescription = new Properties();
//                try {
//                    jobDescription.load(new FileInputStream(jobFile));
//                } catch (IOException ex) {
//                    logger.log(Level.SEVERE, "failed to read example file desription " + example.getName(), ex);
//                    continue;
//                }
//
//                Job exampleJob = new Job();
//
//                exampleJob.setLabel(jobDescription.getProperty("label"));
//                exampleJob.setNotes(jobDescription.getProperty("notes"));
//
//                if (exampleJob.getLabel() == null) {
//                    continue;
//                }
//
//                Date now = new Date();
//                exampleJob.setJobType("Korel");
//                exampleJob.setUws(null);
//                exampleJob.setUwsId(null);
//                exampleJob.setCreatedDate(now);
//                exampleJob.setFinishedDate(now);
//                exampleJob.setStartedDate(now);
//                exampleJob.setOwner(user);
//                exampleJob.setPhase(Phase.COMPLETED);
//
//                this.create(exampleJob);
//
//                // create job folder
//                File jobFolder = this.getFileDir(exampleJob);
//                jobFolder.mkdirs();
//
//                // copy example files
//                try {
//                    FileUtils.copyDirectory(example, jobFolder);
//                } catch (IOException ex) {
//                    logger.log(Level.SEVERE, "cannot copy example files for the job", ex);
//                    this.delete(exampleJob);
//                }
//
//                // delete copied properties file
//                FileUtils.deleteQuietly(new File(jobFolder, "job.properties"));
//            }
//        }
//    }
//
//    public void exportUWSJob(Job job) {
//        File uwsFile = new File(getFileDir(job), "uws-job.xml");
//        try {
//            FileUtils.writeStringToFile(uwsFile, job.getUwsJobXml());
//        } catch (IOException e) {
//            logger.warning("failed to save uws-job.xml");
//        }
//    }
    //==========================Remote job manipulation methods=================

    public void createJob(Job job, String configuration, boolean startImmediately) {
        String request = job.getUws().getUwsUrl();
        if (startImmediately) {
            request += "?PHASE=RUN";
        }
        LOG.log(Level.INFO, "Creating job on {0}", request);
        Map<String, String> bodyParams = new HashMap<>();
        bodyParams.put("config", configuration);
        try {
            UWSJob response = UWSParserManager.getInstance().parseJob(Toolbox.httpPostWithBody(request, bodyParams));
            job.updateFromUWSJob(response);
            edit(job);
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, "Exception during creation of new job", ex);
            job.setPhase(Phase.ERROR);
            edit(job);
        }
    }

    public void startJob(Job job) {
        if (job == null) {
            throw new IllegalArgumentException("Argument job is null");
        }
        if (job.getRemoteId() == null) {
            LOG.log(Level.WARNING, "Job passed as argument has not defined remoteId");
            return;
        }
        try {
            UWSJob response = UWSParserManager.getInstance().parseJob(Toolbox.httpPost(job.getUws().getUwsUrl() + "/" + job.getRemoteId() + "/phase?PHASE=RUN"));
            job.updateFromUWSJob(response);
            edit(job);
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, "Exception during starting job");
            job.setPhase(Phase.ERROR);
            edit(job);
        }
    }

    public void abortJob(Job job) {
        if (job == null) {
            throw new IllegalArgumentException("Argument job is null");
        }
        if (job.getRemoteId() == null) {
            LOG.log(Level.WARNING, "Job passed as argument has not defined remoteId");
            return;
        }
        try {
            UWSJob response = UWSParserManager.getInstance().parseJob(Toolbox.httpPost(job.getUws().getUwsUrl() + "/" + job.getRemoteId() + "/phase?PHASE=ABORT"));
            job.updateFromUWSJob(response);
            edit(job);
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, "Exception during aborting job");
            job.setPhase(Phase.ERROR);
            edit(job);
        }
    }

    public void destroyJob(Job job) {
        if (job == null) {
            throw new IllegalArgumentException("Argument job is null");
        }
        if (job.getRemoteId() == null) {
            LOG.log(Level.WARNING, "Job passed as argument has not defined remoteId");
            return;
        }
        try {
            Toolbox.httpPost(job.getUws().getUwsUrl() + "/" + job.getRemoteId() + "/?ACTION=DELETE");
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, "Exception during destroying job - job will probably have to be deleted manually");
        }
    }

    public UWSJob refreshJob(Job job) {
        if (job == null) {
            throw new IllegalArgumentException("Argument job is null");
        }
        if (job.getRemoteId() == null) {
            LOG.log(Level.WARNING, "Job passed as argument has not defined remoteId");
            return null;
        }
        try {
            UWSJob response = UWSParserManager.getInstance().parseJob(Toolbox.httpGet(job.getUws().getUwsUrl() + "/" + job.getRemoteId()));
            job.updateFromUWSJob(response);
            edit(job);
            return response;
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, "Exception during refreshing job");
            job.setPhase(Phase.ERROR);
            edit(job);
            return null;
        }
    }

}
