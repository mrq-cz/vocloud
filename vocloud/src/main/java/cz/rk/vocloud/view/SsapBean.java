package cz.rk.vocloud.view;

import cz.mrq.vocloud.tools.Toolbox;
import cz.rk.vocloud.ssap.UnparseableVotableException;
import cz.rk.vocloud.ssap.VotableParser;
import cz.rk.vocloud.ssap.model.IndexedSSAPVotable;
import cz.rk.vocloud.ssap.model.Option;
import cz.rk.vocloud.ssap.model.Param;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.PostConstruct;
import javax.faces.application.FacesMessage;
import javax.faces.component.UIComponent;
import javax.faces.component.UISelectItems;
import javax.faces.component.html.HtmlOutputText;
import javax.faces.component.html.HtmlPanelGrid;
import javax.faces.context.FacesContext;
import javax.faces.model.SelectItem;
import javax.faces.view.ViewScoped;
import javax.inject.Named;
import javax.servlet.http.HttpServletRequest;
import org.primefaces.component.inputtext.InputText;
import org.primefaces.component.selectonemenu.SelectOneMenu;
import org.primefaces.event.FileUploadEvent;

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

    private boolean fileUploaded = false;

    private boolean allowDatalink = true;

    private List<FileInfoValue> processedFileInfo;
    private IndexedSSAPVotable parsedVotable;
    private HtmlPanelGrid datalinkPanelGrid = new HtmlPanelGrid();

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
        if (method.equals(this.inputMethod)){
            return;//nothing to change
        }
        this.inputMethod = method;
        //properly reset variables
        votableParsed = false;
        datalinkAvailable = false;
        fileUploaded = false;
        allowDatalink = true;
        parsedVotable = null;
    }

    public String getInputMethod() {
        return inputMethod;
    }

    public void handleFileUpload(FileUploadEvent event) {
        fileUploaded = true;
        processedFileInfo = new ArrayList<>();
        String fileName = new String(event.getFile().getFileName().getBytes(Charset.forName("ISO-8859-1")), Charset.forName("UTF-8"));//this encoding bug could be system specific! --need retest on other sys
        processedFileInfo.add(new FileInfoValue("File name: ", fileName));
        processedFileInfo.add(new FileInfoValue("File size: ", Toolbox.humanReadableByteCount(event.getFile().getSize(), false)));
        try {
            parsedVotable = VotableParser.parseVotable(event.getFile().getInputstream());
        } catch (IOException ex) {
            Logger.getLogger(SsapBean.class.getName()).log(Level.SEVERE, null, ex);
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, "Error uploading file", "File was not uploaded successfully"));
            fileUploaded = false;
            return;
        } catch (UnparseableVotableException ex) {
            Logger.getLogger(SsapBean.class.getName()).log(Level.INFO, "Unparseable votable uploaded", ex);
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_WARN, "Error parsing VOTABLE", "Unable to parse VOTABLE properly"));
            fileUploaded = false;
            return;
        }
        String queryStatus = parsedVotable.getQueryStatus();//can be null if votable has not the format defined by specification
        processedFileInfo.add(new FileInfoValue("Votable query status: ", queryStatus == null ? "undefined" : queryStatus));
        //forbid to continue if query status is error
        if ("ERROR".equals(queryStatus)){
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_WARN, "Error query status", "Votable has ERROR status set"));
        }
        processedFileInfo.add(new FileInfoValue("Record count: ", Integer.toString(parsedVotable.getRows().size())));
        processedFileInfo.add(new FileInfoValue("Datalink: ", parsedVotable.isDatalinkAvailable() ? "available" : "not available"));
        if (parsedVotable.isDatalinkAvailable()) {
            processedFileInfo.add(new FileInfoValue("Datalink access url: ", parsedVotable.getDatalinkResourceUrl()));
            constructDatalinkPanelGrid();
            datalinkAvailable = true;
        }
        votableParsed = true;
    }

    public boolean hasSomeData(){
        if (parsedVotable == null){
            return false;
        }
        return !parsedVotable.getRows().isEmpty();
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

    public boolean isFileUploaded() {
        return fileUploaded;
    }

    public void setFileUploaded(boolean fileUploaded) {
        this.fileUploaded = fileUploaded;
    }

    public List<FileInfoValue> getProcessedFileInfo() {
        return processedFileInfo;
    }

    public void replaceUploadedFile() {
        this.fileUploaded = false;
        this.votableParsed = false;
        this.datalinkAvailable = false;
        this.allowDatalink = true;
        this.processedFileInfo = null;
        this.parsedVotable = null;
    }

    public boolean isAllowDatalink() {
        return allowDatalink;
    }

    public void setAllowDatalink(boolean allowDatalink) {
        this.allowDatalink = allowDatalink;
    }

    private void constructDatalinkPanelGrid() {
        List<UIComponent> children = this.datalinkPanelGrid.getChildren();
        children.clear();
        for (Param p : parsedVotable.getDatalinkInputParams()) {
            if (p.isIdParam()) {
                continue;
            }
            HtmlOutputText label = new HtmlOutputText();
            label.setValue(p.getName() + ": ");
            children.add(label);
            //check if there are some options available
            if (p.getOptions().isEmpty()) {
                //no options - just inputText
                InputText text = new InputText();
                text.setId("datalinkProperty" + p.getName());
                children.add(text);
            } else {
                SelectOneMenu menu = new SelectOneMenu();
                menu.setId("datalinkProperty" + p.getName());
                List<SelectItem> items = new ArrayList<>();
                for (Option o : p.getOptions()) {
                    SelectItem item = new SelectItem();
                    item.setValue(o.getValue());
                    item.setLabel(o.getName());
                    items.add(item);
                }
                UISelectItems uiContainer = new UISelectItems();
                uiContainer.setValue(items);
                menu.getChildren().add(uiContainer);
                children.add(menu);
            }
        }
    }

    public void createDownloadTask() {
        //TODO append message
        HttpServletRequest request = (HttpServletRequest) FacesContext.getCurrentInstance().getExternalContext().getRequest();
        for (Param p : parsedVotable.getDatalinkInputParams()) {
            if (p.isIdParam()) {
                continue;
            }
            String tmp = request.getParameter("mainForm:datalinkProperty" + p.getName() + (p.getOptions().isEmpty() ? "" : "_input"));
            //moreover select one menu has postfix _input at the end
            System.out.println("caught: " + tmp);
        }
    }

    public HtmlPanelGrid getDatalinkPanelGrid() {
        return datalinkPanelGrid;
    }

    public void setDatalinkPanelGrid(HtmlPanelGrid datalinkPanelGrid) {
        this.datalinkPanelGrid = datalinkPanelGrid;
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
