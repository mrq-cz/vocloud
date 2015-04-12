package cz.rk.vocloud.view;

import cz.mrq.vocloud.tools.Toolbox;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.PostConstruct;
import javax.faces.context.FacesContext;
import javax.faces.view.ViewScoped;
import javax.inject.Named;
import org.primefaces.event.FileUploadEvent;
import org.primefaces.event.FlowEvent;

/**
 *
 * @author radio.koza
 */
@Named
@ViewScoped
public class SsapBean implements Serializable {

    private String targetFolder;
    private String votableURL;
    private String downloadedVotable;

    private String inputMethod;
    private boolean votableParsed = false;
    private boolean datalinkAvailable = false;
    private boolean datalinkSolved = false;

    private boolean fileUploaded = false;
    private boolean fileProcessed = false;

    private List<FileInfoValue> processedFileInfo;

    @PostConstruct
    protected void init() {
        targetFolder = (String) FacesContext.getCurrentInstance().getExternalContext().getRequestMap().get("targetFolder");
    }

    public String getTargetFolder() {
        return targetFolder;
    }

    public String getVotableURL() {
        return votableURL;
    }

    public String getDownloadedVotable() {
        return downloadedVotable;
    }

    public void setTargetFolder(String targetFolder) {
        this.targetFolder = targetFolder;
    }

    public void setVotableURL(String votableURL) {
        this.votableURL = votableURL;
    }

    public void setInputMethod(String method) {
        this.inputMethod = method;
    }

    public String getInputMethod() {
        return inputMethod;
    }

    public String onFlowProcess(FlowEvent event) {
        return event.getNewStep();
    }

    public void handleFileUpload(FileUploadEvent event) {
        fileUploaded = true;
        processedFileInfo = new ArrayList<>();
        String fileName = new String(event.getFile().getFileName().getBytes(Charset.forName("ISO-8859-1")), Charset.forName("UTF-8"));//this encoding bug could be system specific! --need retest on other sys
        processedFileInfo.add(new FileInfoValue("File name: ", fileName));
        processedFileInfo.add(new FileInfoValue("File size: ", Toolbox.humanReadableByteCount(event.getFile().getSize(), false)));
        processedFileInfo.add(new FileInfoValue("Format: ", "valid"));
        processedFileInfo.add(new FileInfoValue("Record count: ", "baf"));
        processedFileInfo.add(new FileInfoValue("Datalink: ", "supported"));
    }

    public boolean isVotableParsed() {
        return votableParsed;
    }

    public void setVotableParsed(boolean votableParsed) {
        this.votableParsed = votableParsed;
    }

    public boolean isDatalinkAvailable() {
        return datalinkAvailable;
    }

    public void setDatalinkAvailable(boolean datalinkAvailable) {
        this.datalinkAvailable = datalinkAvailable;
    }

    public boolean isDatalinkSolved() {
        return datalinkSolved;
    }

    public void setDatalinkSolved(boolean datalinkSolved) {
        this.datalinkSolved = datalinkSolved;
    }

    public boolean isFileUploaded() {
        return fileUploaded;
    }

    public void setFileUploaded(boolean fileUploaded) {
        this.fileUploaded = fileUploaded;
    }

    public boolean isFileProcessed() {
        return fileProcessed;
    }

    public void setFileProcessed(boolean fileProcessed) {
        this.fileProcessed = fileProcessed;
    }

    int a = 0;

    public void test() {
        System.out.println("test ajax callback from poll element");
        a++;
        if (a == 5) {
            fileProcessed = true;
            a = 0;
        }
    }

    public List<FileInfoValue> getProcessedFileInfo() {
        return processedFileInfo;
    }

    public void replaceUploadedFile() {
        this.fileUploaded = false;
        this.processedFileInfo = null;
        //todo reset more variables
    }

    public static class FileInfoValue {

        private final String label;
        private final String value;

        public FileInfoValue(String label, String value) {
            this.label = label;
            this.value = value;
        }

        public String getLabel() {
            return label;
        }

        public String getValue() {
            return value;
        }

    }
}
