package cz.mrq.vocloud.managedbeans;

import cz.mrq.vocloud.helper.EditPanel;
import org.apache.commons.io.FileUtils;
import org.primefaces.component.panel.Panel;
import org.primefaces.event.FileUploadEvent;
import org.primefaces.model.UploadedFile;
import org.zeroturnaround.zip.ZipUtil;

import javax.faces.bean.ManagedBean;
import javax.faces.bean.ViewScoped;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * CreateSomJob
 *
 * @author Lumir Mrkva (lumir.mrkva@topmonks.com)
 */
@ManagedBean(name = "createSomJob")
@ViewScoped
public class CreateSomJob extends CreateJob {

    File parameters;

    Boolean zip = false;
    Boolean config = false;
    EditPanel editPanel = new EditPanel();

    static String CONFIG_FILE = "config.json";

    @Override
    public String getJobType() {
        return "SOM";
    }

    @Override
    public void postInit() {
        List<File> files = new ArrayList<>();
        for (File file : uploadedFiles) {
            if (file.getName().endsWith(".zip") && !file.getName().equals("results.zip")) {
                if (zip = ZipUtil.containsEntry(file, CONFIG_FILE)) {
                    parameters = file;
                    files.add(file);
                }
            }
            if (config = file.getName().equals(CONFIG_FILE)) {
                editPanel.setFileContents(file);
            }
        }
        uploadedFiles = files;
    }

    @Override
    protected File prepareParameters() {
        ZipUtil.replaceEntry(parameters, CONFIG_FILE, editPanel.getFileContents().getBytes());
        return parameters;
    }

    @Override
    public void handleFileUpload(FileUploadEvent event) {
        UploadedFile uploaded = event.getFile();
        if (uploaded.getFileName().endsWith(".zip")) {
            File file = new File(uploadDir, uploaded.getFileName());
            copyUploadedFile(file, uploaded);
            if (zip = ZipUtil.containsEntry(file, CONFIG_FILE)) {
                uploadedFiles.add(file);
                parameters = file;
                String config = new String(ZipUtil.unpackEntry(file, CONFIG_FILE));
                editPanel.setFileContents(config);
            }
        }
    }

    @Override
    public void handleSave() {

    }

    public Boolean getZip() {
        return zip;
    }

    public EditPanel getEditPanel() {
        return editPanel;
    }
}
