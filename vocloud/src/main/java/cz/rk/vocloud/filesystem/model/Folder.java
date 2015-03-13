package cz.rk.vocloud.filesystem.model;

import java.util.Date;

/**
 *
 * @author radio.koza
 */
public class Folder extends FilesystemItem{
    
    public Folder(String folderName, String relativeDir){
        super(folderName, relativeDir);
    }

    @Override
    public Long getSizeInBytes() {
        return null;
    }

    @Override
    public Date getLastModified() {
        return null;
    }

    @Override
    public boolean isFolder() {
        return true;
    }
    
    
}
