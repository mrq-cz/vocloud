package cz.rk.vocloud.downloader;

import cz.rk.vocloud.entity.DownloadJob;
import cz.rk.vocloud.entity.DownloadState;
import cz.rk.vocloud.filesystem.FilesystemManipulator;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Date;
import java.util.LinkedList;
import java.util.Queue;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.ejb.Asynchronous;
import javax.ejb.ConcurrencyManagement;
import javax.ejb.ConcurrencyManagementType;
import javax.ejb.EJB;
import javax.ejb.LocalBean;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 *
 * @author radio.koza
 */
@Stateless
@LocalBean
@ConcurrencyManagement(ConcurrencyManagementType.BEAN)
public class DownloadProcessor {

    private static final Logger LOG = Logger.getLogger(DownloadProcessor.class.getName());

    @EJB
    private DownloadJobFacade djb;
    @EJB
    private FilesystemManipulator fsm;

    //===============================ASYNCHRONOUS DOWNLOADING METHOD============
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    @Asynchronous
    public void processDownloadJob(DownloadJob job) {
        LOG.log(Level.INFO, "Downloading job {0}", job.getDownloadUrl());
        StringBuilder downloadLog = new StringBuilder();
        //change state
        job.setState(DownloadState.RUNNING);
        djb.edit(job);
        boolean success = false;
        try {
            success = startDownloading(job.getDownloadUrl(), job.getSaveDir(), downloadLog);
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "Runtime exception during job processing", ex);
            downloadLog.append("Runtime exception thrown\n").append(ex.toString()).append('\n');
            success = false;
        }
        if (success) {
            job.setState(DownloadState.FINISHED);
            LOG.log(Level.INFO, "Download job {0} was finished", job.getDownloadUrl());
        } else {
            job.setState(DownloadState.FAILED);
            LOG.log(Level.INFO, "Download job {0} was finished with one or more exceptions", job.getDownloadUrl());
        }
        job.setFinishTime(new Date());
        job.setMessageLog(downloadLog.toString());//todo
        djb.edit(job);
    }

    //=================================UTIL=====================================
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    private String pathDirectoryCut(String originPath, final int directoryCut) {
        String[] splitArray = originPath.split("/");
        StringBuilder path = new StringBuilder();
        for (int i = directoryCut + 1; i < splitArray.length; i++) {
            path.append(splitArray[i]);
            if (i != splitArray.length - 1) {
                path.append('/');
            }
        }
        return path.toString();
    }

    //==================================HTTP downloading========================
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    private boolean processHttpLink(URL url, final int directoryCut, String saveDir, StringBuilder downloadLog) {
//        System.out.println("processing " + url);
        boolean success = true;
        HttpURLConnection conn = null;
        try {
            try {
                conn = (HttpURLConnection) url.openConnection();
            } catch (IOException ex) {
                downloadLog.append("Unable to open http connection with ").append(url.toExternalForm()).append("\n").append(ex.toString()).append('\n');
                return false;
            }
            String ct = conn.getContentType();
            String charset = null;
            if (ct == null) {
//            System.err.println("Content type is null");
            } else {
                String[] splitArray = ct.split(";");
                ct = splitArray[0];
                if (splitArray.length > 1) {
                    for (int i = 1; i < splitArray.length; i++) {
                        if (splitArray[i].contains("charset=")) {
                            charset = splitArray[i].replace("charset=", "").trim();
//                        System.out.println("charset defined: " + charset);
                            break;
                        }
                    }
                }
//            System.out.println("Content type: " + ct);
            }
            if (ct != null && ct.equals("text/html")) {
                //download recursively
                Document doc = null;
                try {
                    doc = Jsoup.parse(conn.getInputStream(), charset, conn.getURL().toExternalForm());
                } catch (IOException ex) {
                    downloadLog.append("Unprocessable resource address ").append(url.toExternalForm()).append('\n').append(ex.toString()).append('\n');
                    return false;
                }
                Elements links = doc.select("a[href]");
                Queue<String> queue = new LinkedList<>();
                for (Element link : links) {
                    String href = link.attr("abs:href");
                    href = href.replaceAll("\\?.*$", "");
                    if (!href.matches(conn.getURL().toExternalForm() + ".+")) {
                        continue;
                    }
                    queue.add(href);
//                System.out.println("   " + href);

                }
                while (!queue.isEmpty()) {
                    try {
                        if (!processHttpLink(new URL(queue.poll()), directoryCut, saveDir, downloadLog)) {
                            success = false;
                        }
                    } catch (IOException ex) {
                    }
                }
            } else {
                String fileName = pathDirectoryCut(conn.getURL().getPath().split("\\?")[0], directoryCut);
                if (fileName.equals("")) {
                    fileName = conn.getURL().getPath().split("\\?")[0].replaceAll("^.*/([^/]+)$", "$1");
//                System.out.println("Filename " + fileName);
                }
                try {
                    fileName = URLDecoder.decode(fileName, "UTF-8");
                } catch (UnsupportedEncodingException ex) {
                    //should not be thrown if charset is set properly
                    Logger.getLogger(DownloadProcessor.class.getName()).log(Level.SEVERE, null, ex);
                }
                try {
                    boolean result = fsm.saveDownloadedFileIfNotExists(saveDir + fileName, conn.getInputStream(), true);
                    if (!result) {
                        downloadLog.append("File: ").append(fileName).append(" already exists\n");
                    }
                } catch (IOException ex) {
                    downloadLog.append("Exception during downloading and saving file ").append(fileName).append("\n").append(ex.toString()).append('\n');
                    return false;
                }
            }
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
        return success;
    }

    //==================================FTP=====================================
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    private boolean ftpFileDownload(FTPClient ftp, FTPFile file, String base, final int directoryCut, StringBuilder downloadLog) {
        try {
            ftp.setFileType(FTPClient.BINARY_FILE_TYPE);
        } catch (IOException ex) {
            downloadLog.append("Ftp server binary transfer was not set properly. Files may be corrupted\n");
        }
        String workingDir = null;
        try {
            workingDir = ftp.printWorkingDirectory();
        } catch (IOException ex) {
            downloadLog.append("Connection with the ftp server was lost\n").append(ex.toString()).append('\n');
            return false;
        }
        String remoteFile = workingDir + "/" + file.getName();
        boolean fileExists = fsm.fileExists(base + pathDirectoryCut(workingDir, directoryCut) + file.getName());
        if (fileExists) {
            downloadLog.append("File ").append(file.getName()).append(" already exists\n");
        } else {
            try {
                boolean saveResult = fsm.saveDownloadedFileIfNotExists(base + pathDirectoryCut(workingDir, directoryCut) + file.getName(), ftp.retrieveFileStream(remoteFile), false);
                if (!saveResult) {
                    downloadLog.append("File ").append(file.getName()).append(" already exists\n");//should not happen
                }
                if (!ftp.completePendingCommand()) {
                    downloadLog.append("Pending command was not successfully completed. File ").append(file.getName()).append(" may be corrupted\n");
                }
            } catch (IOException ex) {
                Logger.getLogger(DownloadProcessor.class.getName()).log(Level.SEVERE, null, ex);
                downloadLog.append("Exception thrown during downloading file ").append(file.getName()).append(" from ftp server\n").append(ex.toString()).append('\n');
                return false;
            }
        }
        return true;
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    private boolean processFtpDirectory(FTPClient ftp, final int directoryCut, String baseDir, StringBuilder downloadLog) throws IOException {
        boolean success = true;
        FTPFile[] ftpFiles = ftp.listFiles();//list files of the ftp folder
        for (FTPFile file : ftpFiles) {//iterate over items
            if (file.isDirectory()) {
                ftp.changeWorkingDirectory(file.getName());
                if (!processFtpDirectory(ftp, directoryCut, baseDir, downloadLog)) {
                    success = false;
                }
                ftp.changeToParentDirectory();
            } else {
                if (!ftpFileDownload(ftp, file, baseDir, directoryCut, downloadLog)) {
                    success = false;
                }
            }
        }
        return success;
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    private boolean processFtpLink(URL url, final int directoryCut, String baseDir, StringBuilder downloadLog) {
        boolean success = true;
        FTPClient ftp = new FTPClient();
        ftp.setConnectTimeout(20000);
        try {
            ftp.connect(url.getHost());
            if (!ftp.login("anonymous", "")) {//login credentials not supported yet - only anonymous login
                ftp.logout();
                downloadLog.append("Ftp login was unsuccessful\n");
                return false;
            }
        } catch (IOException ex) {
            downloadLog.append("Unable to connect to ftp server\n");
            return false;
        }
        int reply = ftp.getReplyCode();
        if (!FTPReply.isPositiveCompletion(reply)) {
            downloadLog.append("Ftp server did not respond with proper response code: ").append(reply);
            try {
                ftp.disconnect();
            } catch (IOException ex) {
            }
            return false;
        }
        //entering local passive ftp mode
        ftp.enterLocalPassiveMode();
        try {
            //change working dir if possible
            //it is important here to distinguish if the path is folder of file
            if (!ftp.changeWorkingDirectory(url.getPath())) {
                //path is invalid or file
                FTPFile[] file = ftp.listFiles(url.getPath());//check files in this path
                if (file.length == 1) {
                    //path represents single file
                    ftp.changeWorkingDirectory(url.getPath().replaceAll("[^/]+$", ""));
                    if (!ftpFileDownload(ftp, file[0], baseDir, directoryCut, downloadLog)) {
                        success = false;
                    }
                } else {
                    //probably invalid directory
                    downloadLog.append("Directory in ftp server: ").append(url.getPath()).append(" does not point to valid location");
                    return false;
                }
            } else {
                //directory is correct
                if (!processFtpDirectory(ftp, directoryCut, baseDir, downloadLog)) {
                    success = false;
                }
            }
        } catch (IOException ex) {
            downloadLog.append("Connection with ftp server was lost\n").append(ex.toString()).append('\n');
            return false;
        }

        try {
            //logout
            ftp.logout();
            //disconnect ftp client
            ftp.disconnect();
        } catch (IOException ex) {
            downloadLog.append("Logout from ftp server was unsuccessful");
        }
        return success;
    }

    //===========================MAIN DOWNLOADING START POINT===================
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    private boolean startDownloading(String link, String saveDir, StringBuilder downloadLog) {
        boolean resultSuccess = false;
        try {
            URL url = new URL(link);

            String path = url.getPath();
            if (path.matches(".+/$")) {
                path = path.substring(0, path.length() - 1);
            }
            int directoryCut = path.split("/").length - 1;
            switch (url.getProtocol().toUpperCase()) {
                case "HTTP":
                case "HTTPS":
                    resultSuccess = processHttpLink(url, directoryCut, saveDir, downloadLog);
                    break;
                case "FTP":
                    resultSuccess = processFtpLink(url, directoryCut, saveDir, downloadLog);
                    break;
                default:
                    LOG.log(Level.WARNING, "Unknown download protocol {0}", url.getProtocol().toUpperCase());
                    break;
            }
        } catch (MalformedURLException ex) {
            downloadLog.append("URL is malformed!\n").append(ex.toString()).append('\n');
            //this should not happen here. malformed url should be checked during creation of the download job
        }
        return resultSuccess;
    }
}
