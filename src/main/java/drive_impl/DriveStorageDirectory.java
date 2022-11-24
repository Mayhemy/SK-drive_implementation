package drive_impl;

import Exceptions.*;
import OperationsAndExtensions.Extensions;
import Storage.StorageAndCfg.Cfg;
import Storage.StorageAndCfg.StorageSpec;

import java.util.Collection;
import java.util.List;

public class DriveStorageDirectory{
    private String root;
    private String rootID;
    private long maxByteSize;
    private int maxNumberOfFiles;
    //private Cfg config;
    private String downloadFolder;
    private boolean fileSizeSet = false;
    private boolean maxNumberOfFilesSet = false;

    public String getRoot() {
        return root;
    }
    public String getRootID() {
        return rootID;
    }

    public long getMaxByteSize() {
        return maxByteSize;
    }

    public int getMaxNumberOfFiles() {
        return maxNumberOfFiles;
    }

    public String getDownloadFolder() {
        return downloadFolder;
    }

    public boolean isFileSizeSet() {
        return fileSizeSet;
    }

    public boolean isMaxNumberOfFilesSet() {
        return maxNumberOfFilesSet;
    }

    public void setRoot(String root) {
        this.root = root;
    }

    public void setRootID(String rootID) {
        this.rootID = rootID;
    }

    public void setMaxByteSize(long maxByteSize) {
        this.maxByteSize = maxByteSize;
    }

    public void setMaxNumberOfFiles(int maxNumberOfFiles) {
        this.maxNumberOfFiles = maxNumberOfFiles;
    }

    public void setDownloadFolder(String downloadFolder) {
        this.downloadFolder = downloadFolder;
    }

    public void setFileSizeSet(boolean fileSizeSet) {
        this.fileSizeSet = fileSizeSet;
    }

    public void setMaxNumberOfFilesSet(boolean maxNumberOfFilesSet) {
        this.maxNumberOfFilesSet = maxNumberOfFilesSet;
    }
}
