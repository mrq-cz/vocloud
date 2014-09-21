package cz.mrq.vocloud.managedbeans;

import org.primefaces.event.FileUploadEvent;
import org.primefaces.model.UploadedFile;

import javax.faces.bean.ManagedBean;
import javax.faces.bean.ViewScoped;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
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

    @Override
    public String getJobType() {
        return "SOM";
    }

    @Override
    public void postInit() {
        List<File> files = new ArrayList<>();
        for (File f : uploadedFiles) {
            if (f.getName().endsWith(".zip") && !f.getName().equals("results.zip")) {
                zip = true;
                parameters = f;
                files.add(f);
            }
        }
        uploadedFiles = files;
    }

    @Override
    protected File prepareParameters() {
        return parameters;
    }

    @Override
    public void handleFileUpload(FileUploadEvent event) {
        UploadedFile uploaded = event.getFile();
        if (uploaded.getFileName().endsWith(".zip")) {
            File file = new File(uploadDir, uploaded.getFileName());
            copyUploadedFile(file, uploaded);
            uploadedFiles.add(file);
            parameters = file;
            zip = true;
        }
    }

    @Override
    public void handleSave() {

    }

    public Boolean getZip() {
        return zip;
    }
}
