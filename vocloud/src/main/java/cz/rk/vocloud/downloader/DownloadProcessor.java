/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cz.rk.vocloud.downloader;

import cz.rk.vocloud.entity.DownloadJob;
import cz.rk.vocloud.entity.DownloadState;
import cz.rk.vocloud.filesystem.FilesystemManipulator;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
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

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    @Asynchronous
    public void processDownloadJob(DownloadJob job) {
        LOG.log(Level.INFO, "Downloading job {0}", job.getDownloadUrl());
        StringBuilder downloadLog = new StringBuilder();
        //change state
        job.setState(DownloadState.RUNNING);
        djb.edit(job);
        //todo download itself
        startDownloading(job.getDownloadUrl(), job.getSaveDir(), downloadLog);
        job.setState(DownloadState.FINISHED);
        job.setFinishTime(new Date());
        job.setMessageLog(downloadLog.toString());//todo
        djb.edit(job);
    }

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

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    private void processHttpLink(URL url, final int directoryCut, String saveDir, StringBuilder downloadLog) {
//        System.out.println("processing " + url);
        HttpURLConnection conn;
        try {
            conn = (HttpURLConnection) url.openConnection();
        } catch (IOException ex) {
            downloadLog.append("Unable to open http connection with ").append(url.toExternalForm()).append("\n").append(ex.toString());
            return;
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
                downloadLog.append("Connection lost with ").append(url.toExternalForm()).append('\n').append(ex.toString());
                return;
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
                    processHttpLink(new URL(queue.poll()), directoryCut, saveDir, downloadLog);
                } catch (IOException ex){}
            }
        } else {
            String fileName = pathDirectoryCut(conn.getURL().getPath().split("\\?")[0], directoryCut);
            if (fileName.equals("")) {
                fileName = conn.getURL().getPath().split("\\?")[0].replaceAll("^.*/([^/]+)$", "$1");
//                System.out.println("Filename " + fileName);
            }
            try {
                boolean result = fsm.saveUploadedFileIfNotExists(saveDir + fileName, conn.getInputStream());
                if (!result){
                    downloadLog.append("File: ").append(fileName).append(" already exists\n");
                }
            } catch (IOException ex){
                downloadLog.append("Exception during downloading and saving file ").append(fileName).append("\n").append(ex.toString());
            }
        }
        conn.disconnect();
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    private void startDownloading(String link, String saveDir, StringBuilder downloadLog) {
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
                    processHttpLink(url, directoryCut, saveDir, downloadLog);
                    break;
                case "FTP":
//                    processFtpLink(url, directoryCut, downloadLog);
                    break;
                default:
                    System.out.println("Unknown protocol");
                    break;
            }
        } catch (MalformedURLException ex) {
            downloadLog.append("URL is malformed!\n").append(ex.toString());
        }
    }
}