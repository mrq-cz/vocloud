package cz.rk.vocloud.filesystem;

import cz.mrq.vocloud.tools.Config;
import cz.rk.vocloud.filesystem.model.FilesystemFile;
import cz.rk.vocloud.filesystem.model.FilesystemItem;
import cz.rk.vocloud.filesystem.model.Folder;
import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.PostConstruct;
import javax.ejb.LocalBean;
import javax.ejb.Stateless;
import javax.inject.Inject;

/**
 *
 * @author radio.koza
 */
@LocalBean
@Stateless
public class FilesystemManipulator {
    
    @Inject
    @Config
    private String filesystemDir;
    private static final Logger LOG = Logger.getLogger(FilesystemManipulator.class.getName());
    
    private File filesystemDirectory;
    
    @PostConstruct
    private void init(){
        //initialize filesystem directory from String and check right access permissions
        filesystemDirectory = new File(filesystemDir).getAbsoluteFile();
        //check that directory exists
        if (!filesystemDirectory.exists()){
            LOG.info("Filesystem directory does not exist. Creating...");
            if (!filesystemDirectory.mkdirs()){
                LOG.severe("Unable to create filesystem directory path. Do you have permissions?");
                filesystemDirectory = null;
                return;
            }
        }
        //check that it is really directory
        if (!filesystemDirectory.isDirectory()){
            //if not, use parent directory of this file
            filesystemDirectory = filesystemDirectory.getParentFile().getAbsoluteFile();
        }
        if (!filesystemDirectory.canRead()){
            LOG.severe("Unable to read filesystem directory");
        }
        if (!filesystemDirectory.canWrite()){
            LOG.severe("Missing permissions to write into filesystem directory");
        }
    }
    
    
    public List<FilesystemItem> listFilesystemItems(String prefix){
        if (filesystemDirectory == null){
            throw new IllegalStateException("Filesystem directory is uninitialized");
        }
        File[] files = filesystemDirectory.listFiles();
        List<Folder> folders = new ArrayList<>();
        List<FilesystemFile> fsFiles = new ArrayList<>();
        for (File i: files){
            if (i.isDirectory()){
                folders.add(new Folder(i.getName(), prefix));
            } else if (i.isFile()){
                fsFiles.add(new FilesystemFile(i.getName(), prefix, i.length(), new Date(i.lastModified())));
            } else {
                //should not happen
                LOG.log(Level.WARNING, "Nor directory nor file! {0}", i.getPath());
            }
        }
        //merge collections
        List<FilesystemItem> result = new ArrayList<>(files.length);
        result.addAll(folders);
        result.addAll(fsFiles);
        return result;
    }
    
    public List<FilesystemItem> listFilesystemItems(){
        //consider prefix as "/"
        return listFilesystemItems("/");
    }
    
}
