package drive_impl;

import Exceptions.*;
import Exceptions.FileNotFoundException;
import Storage.StorageAndCfg.Cfg;
import Storage.StorageAndCfg.StorageManager;
import Storage.StorageAndCfg.StorageSpec;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

import java.io.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class DriveStorage extends StorageSpec {

    static {
        StorageManager.defineStorage(new DriveStorage());
    }

    private DriveStorageDirectory currentDriveStorageDirectory;
    private Drive drive;



    // Basic Google Setup
    /**
     * Application name.
     */
    private static final String APPLICATION_NAME = "My project";

    /**
     * Global instance of the {@link FileDataStoreFactory}.
     */
    private static FileDataStoreFactory DATA_STORE_FACTORY;

    /**
     * Global instance of the JSON factory.
     */
    private static final JacksonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

    /**
     * Global instance of the HTTP transport.
     */
    private static HttpTransport HTTP_TRANSPORT;

    /**
     * Global instance of the scopes required by this quickstart.
     * <p>
     * If modifying these scopes, delete your previously saved credentials at
     * ~/.credentials/calendar-java-quickstart
     */
    private static final List<String> SCOPES = Arrays.asList(DriveScopes.DRIVE);

    static {
        try {
            HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Creates an authorized Credential object.
     *
     * @return an authorized Credential object.
     * @throws IOException
     */
    public static Credential authorize() throws IOException {
        // Load client secrets.
        InputStream in = DriveStorage.class.getResourceAsStream("/client_secret.json");

        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        // Build flow and trigger user authorization request.
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT, JSON_FACTORY,
                clientSecrets, SCOPES).setAccessType("offline").build();
        Credential credential = new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");
        return credential;
    }

    /**
     * Build and return an authorized Calendar client service.
     *
     * @return an authorized Calendar client service
     * @throws IOException
     */
    public static Drive getDriveService() throws IOException {
        Credential credential = authorize();
        return new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    // End of Setup
    //Assisting methods
    public String getID(String s){
        FileList result = null;
        String id = null;
        try{
            result = drive.files().list()
                    .execute();
        }catch(IOException e) {
            e.printStackTrace();
        }
        List<File> files = result.getFiles();
        for(File f:files){
            if(f.getName().equalsIgnoreCase(s)){
                id = f.getId();
            }
        }
        return id;
    }
    // Da li ovde treba private?
    public String getFileIDfromPath(String path){
        if(path.equals(".") || path.equals("C:\\")) //<-- added last, not sure if it works
            return "root";
        List<String> parents = new ArrayList<>();
        StringTokenizer tokenizer = new StringTokenizer(path, "\\");
        while (tokenizer.hasMoreElements()) {
            parents.add(tokenizer.nextToken());
        }
        System.out.println(parents.toString());
        //let's assume that the path provided is this format C:\\Folder1\\Folder2\\...\\File
        String folderID = null;
        int counter = 2;
        for(String folderName:parents){
            //We're skipping C:\\
            if(counter == 2){
                counter--;
                continue;
            }
            if(counter == 1){
                counter--;
                System.out.println(folderID);
                folderID = getID(folderName);
                System.out.println(folderID);
                continue;
            }
            try {
                System.out.println(folderName);
                System.out.println(folderID);
                System.out.println("Pre");
                FileList containsList = drive.files().list()
                        .setQ("'"+folderID+"'"+" in parents")
                        .execute();
                System.out.println("Post");
                if(!containsList.isEmpty()) {
                    for (File f : containsList.getFiles()) {
                        if (f.getName().equals(folderName))
                            folderID = f.getId();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        System.out.println(folderID);
        return folderID;
    }
    public Collection<String> findAllSubfiles(Collection<String> allSubfiles, String folderID) throws IOException {
        Collection<String> newFiles = new ArrayList<>();
        newFiles = allSubfiles;
        FileList fileList = null;
        fileList = drive.files().list()
                .setQ("'"+folderID+"'"+" in parents")
                .execute();
        for(File f:fileList.getFiles()){
            if(!f.getMimeType().equalsIgnoreCase("application/vnd.google-apps.folder"))
                newFiles.add("Name: "+f.getName()+";ID: "+f.getId());
        }
        for(File f:fileList.getFiles()){
            if(f.getMimeType().equalsIgnoreCase("application/vnd.google-apps.folder"))
                newFiles.addAll(findAllSubfiles(newFiles,f.getId()));
        }
        return newFiles;
    }

    public boolean checkIfInStorage(String dirpath) throws IOException {
        String fileID = getFileIDfromPath(dirpath);
        File testFile = drive.files().get(fileID).execute();
        if(testFile.getId().equals(currentDriveStorageDirectory.getRootID())){
            System.out.println("File ID equals Storage Root ID");
            return true;}
        File file = drive.files().get(fileID).execute();
        Collection<String> storageFiles = new ArrayList<>();
        storageFiles = findAllFiles(storageFiles,currentDriveStorageDirectory.getRootID());//was fileID
        System.out.println("All the files in Storage: "+storageFiles.toString());
        if(storageFiles.contains("Name: "+file.getName()+";ID: "+file.getId())){
            return true;}
        return false;
    }
    public Collection<String> findAllFiles(Collection<String> allFiles, String fileID) throws IOException {
        Collection<String> newFiles = new ArrayList<>();
        newFiles = allFiles;
        FileList folderList = null;
        folderList = drive.files().list()
                .setQ("'" + fileID + "'" + " in parents")
                .execute();
        for (File f : folderList.getFiles()) {
            //if (f.getMimeType().equalsIgnoreCase("application/vnd.google-apps.folder")) //Shouldn't we add all the files regardless of the whether they are folders or not???
            newFiles.add("Name: " + f.getName() + ";ID: " + f.getId());
        }
        for (File f : folderList.getFiles()) {
            if (f.getMimeType().equalsIgnoreCase("application/vnd.google-apps.folder"))
                newFiles.addAll(findAllFiles(newFiles, f.getId()));
        }
        return newFiles;
    }
    public boolean checkIfParent(String dirpath,String fileID) throws IOException {;
        File file = drive.files().get(fileID).execute();
        Collection<String> storageFiles = new ArrayList<>();
        storageFiles = findAllSubfiles(storageFiles,dirpath);
        if(storageFiles.contains("Name: "+file.getName()+";ID: "+file.getId()))
            return true;
        return false;
    }

    //
    // Implementation of abstract class methods
    @Override
    public void saveConfig(Cfg cfg) {

    }

    @Override
    public void createStorage(String pathToFolder, String folderName) throws FolderNotFoundException {
        try {
            drive = getDriveService();
        } catch (IOException e) {
            e.printStackTrace();
        }
        String holdingFolderID = getFileIDfromPath(pathToFolder);
        System.out.println("Hope this is the correct ID: "+holdingFolderID);
        try {
            File testFile = drive.files().get(holdingFolderID).execute();
            System.out.println("Name of this ID: "+testFile.getName());
        } catch (IOException e) {
            e.printStackTrace();
        }
        File fileMetadata = new File();
        fileMetadata.setName(folderName);
        fileMetadata.setMimeType("application/vnd.google-apps.folder");
        fileMetadata.setDescription("Implemented Drive Storage");
        fileMetadata.setParents(Collections.singletonList(holdingFolderID));

        try {
            File file = drive.files().create(fileMetadata)
                    .setFields("id, parents, name, mimeType")
                    .execute();
            System.out.println("Folder ID: " + file.getId());
            currentDriveStorageDirectory = new DriveStorageDirectory();
            currentDriveStorageDirectory.setRootID(file.getId()); // changed this from file.getId() to fileMetadata.getId()
            currentDriveStorageDirectory.setRoot(file.getName()); //
            String downloadsDir = System.getProperty("user.home")+"/Downloads/";
            currentDriveStorageDirectory.setDownloadFolder(downloadsDir);
            FileList filelist = drive.files().list()
                    .setQ("'"+holdingFolderID+"'"+" in parents")
                    .execute();
            System.out.println("All the files and folders in :"+holdingFolderID+" ID");
            for(File f:filelist.getFiles())
                System.out.println(f.getName());
        } catch (GoogleJsonResponseException e) {
            // TODO(developer) - handle error appropriately
            System.err.println("Unable to create folder: " + e.getDetails());
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("Storage Created at "+ currentDriveStorageDirectory.getRoot()); // <-- Root still needs to be set
        System.out.println("Storage Created at "+ currentDriveStorageDirectory.getRootID()); // <-- Root still needs to be set
    }

    //implement later when cfg is understood
    @Override
    public void createStorage(String pathToFolder, String folderName, String configLocation) throws FileNotFoundException {

    }

    @Override
    public void createDirectories(String path, String... dirNames) throws FolderNotFoundException, UnsupportedOperationException {
        String folderID = getFileIDfromPath(path);
        if(folderID == null)
            throw new FolderNotFoundException();
        for(String s : dirNames){
            String folderName = s;

            File fileMetadata = new File();
            fileMetadata.setName(folderName);
            fileMetadata.setMimeType("application/vnd.google-apps.folder");
            fileMetadata.setParents(Collections.singletonList(folderID));

            File file = null;

            try {
                file = drive.files().create(fileMetadata).setFields("id,name,parents,mimeType").execute();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void createDirectoriesFormated(String s, String... strings) throws FolderNotFoundException, UnsupportedOperationException {
        // Sve kao prethodna metoda, samo dodati detekciju paterna
    }



    @Override
    public void loadFiles(String path,String... localPaths) throws FileNotFoundException, MaxStorageSizeException, MaxNumberOfFilesExceededException, UnsupportedOperationException {
        //we need a file/files from a local drive, and also the id of a folder, so we can set their parent ID to that folder's file ID

        String folderID = getFileIDfromPath(path);
        for (String pth:localPaths){
            java.io.File filePath = new java.io.File(pth);
            File fileMetadata = new File();
            fileMetadata.setName(filePath.getName());
            fileMetadata.setParents(Collections.singletonList(folderID));
            try {
                File file = drive.files().create(fileMetadata)
                            .setFields("id, parents")
                            .execute();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void deleteFiles(String... filePaths) throws FileNotFoundException, UnsupportedOperationException {
        String fileID;
        for (String s : filePaths) {
            fileID = getFileIDfromPath(s);
            File fileMetadata = null;
            try {
                fileMetadata = drive.files().get(fileID).execute();
            } catch (IOException e) {
                e.printStackTrace();
            }

            if(!fileMetadata.getMimeType().equalsIgnoreCase("application/vnd.google-apps.folder")) {
                try {
                    drive.files().delete(fileID).execute();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
    @Override
    public void deleteDirectories(String... folderPaths) throws FolderNotFoundException, UnsupportedOperationException {
        String fileID;
        for (String s : folderPaths) {
            fileID = getFileIDfromPath(s);
            File fileMetadata = null;
            try {
                fileMetadata = drive.files().get(fileID).execute();
            } catch (IOException e) {
                e.printStackTrace();
            }

            if(fileMetadata.getMimeType().equalsIgnoreCase("application/vnd.google-apps.folder")) {
                try {
                    drive.files().delete(fileID).execute();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void moveFile(String source, String destination) throws FileNotFoundException, FolderNotFoundException, MaxStorageSizeException, MaxNumberOfFilesExceededException, UnsupportedOperationException {
        String destFolderID = getFileIDfromPath(destination);
        if(destFolderID == null){
            throw new FolderNotFoundException();
        }
        File destFolder = null;
        try {
            destFolder = drive.files().get(destFolderID).execute();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if(destFolder.getMimeType()!="application/vnd.google-apps.folder"){
            throw new FolderNotFoundException();
        }
        List<String> fileList = destFolder.getSpaces();
        if(currentDriveStorageDirectory.isMaxNumberOfFilesSet()) {
            if (currentDriveStorageDirectory.getMaxNumberOfFiles() < fileList.size()) {
                throw new MaxNumberOfFilesExceededException();
            }
        }
        String sourceFileID = getFileIDfromPath(source);
        File fileMetadata = null;
        try {
            fileMetadata = drive.files().get(sourceFileID).setFields("parents").execute();
        } catch (IOException e) {
            e.printStackTrace();
        }
        StringBuilder previousParents = new StringBuilder();
        for (String parent : fileMetadata.getParents()) {
            previousParents.append(parent);
            previousParents.append(',');
        }
        try {
            fileMetadata = drive.files().update(sourceFileID, null)
                    .setAddParents(destFolderID)
                    .setRemoveParents(previousParents.toString())
                    .setFields("id, parents, mimeType")
                    .execute();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //TODO destination String not used | Slight error when downloading an empty file
    @Override
    public void downloadFile(String source, String destination) throws FileNotFoundException, FolderNotFoundException, UnsupportedOperationException {
        String fileID = getFileIDfromPath(source);
        System.out.println("File ID of downloaded file is: "+fileID);
        String fileName = null;
        try {
            fileName = drive.files().get(fileID).execute().getName();
        } catch (IOException e) {
            e.printStackTrace();
        }
        //OutputStream outputStream = new ByteArrayOutputStream();
        OutputStream outputStream = null;
        try {
            outputStream = new FileOutputStream(currentDriveStorageDirectory.getDownloadFolder()+"/"+fileName);
        } catch (java.io.FileNotFoundException e) {
            e.printStackTrace();
        }
        try {
            drive.files().get(fileID)
                    .executeMediaAndDownloadTo(outputStream);
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            assert outputStream != null;
            outputStream.flush();
            outputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @Override
    public void renameFile(String path, String fileName) throws FileNotFoundException, FolderNotFoundException, InvalidNameException, UnsupportedOperationException {
        String fileID = getFileIDfromPath(path);
        File filemetadata = new File();
       /* try {
            file = drive.files().get(fileID).execute();
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }*/
        filemetadata.setName(fileName);
        try {
            drive.files().update(fileID,filemetadata)
                    .setFields("name")
                    .execute();

        } catch (IOException e) {
            e.printStackTrace();
        }

    }


    @Override
    public Collection<String> retFiles(String dirpath) throws FolderNotFoundException {
        /*String folderID = getFileIDfromPath(dirpath);
        FileList fileList = null;
        try {
            fileList = drive.files().list()
                    .setQ("'"+folderID+"'"+" in parents")
                    .execute();
        } catch (IOException e) {
            e.printStackTrace();
        }
        Collection<String> retFiles = new ArrayList<>();
        for(File f:fileList.getFiles()){
            retFiles.add("Name: "+f.getName()+"\nID:"+f.getId()+"MimeType: "+f.getMimeType()+"\nSize: "+f.getSize()+"\nMimeType: "+f.getMimeType()+"\n");
        }*/
        //TODO Just subfiles of this directory
        try {
            if(!checkIfInStorage(dirpath)){
                System.out.println(getFileIDfromPath(dirpath));
                File file = drive.files().get(getFileIDfromPath(dirpath)).execute();
                File fileroot = drive.files().get(currentDriveStorageDirectory.getRootID()).execute();
                System.out.println(file.getName());
                System.out.println(file.getId());
                System.out.println(fileroot.getName());
                System.out.println(fileroot.getId());
                System.out.println(currentDriveStorageDirectory.getRootID());
                System.out.println(currentDriveStorageDirectory.getRoot());
                System.out.println("Path outside of Storage");
                return null;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        String folderID = getFileIDfromPath(dirpath);
        Collection<String> retFiles = new ArrayList<>();
        FileList fileList = null;
        try {
            fileList = drive.files().list().setQ("'"+folderID+"'"+" in parents").execute();
        } catch (IOException e) {
            e.printStackTrace();
        }
        for(File f:fileList.getFiles()){
            retFiles.add("Name: "+f.getName()+";ID: "+f.getId());
        }
        if(retFiles.isEmpty())
            retFiles.add("Nema Rezultata");
        return retFiles;
    }

    @Override
    public Collection<String> retSubdirFiles(String dirpath) throws FolderNotFoundException {
        try {
            if(!checkIfInStorage(dirpath)){
                System.out.println("Path outside of Storage");
                return null;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        Collection<String> retSubdirFiles = new ArrayList<>();

        String folderID = getFileIDfromPath(dirpath);
        FileList folderList = null;
        try {
            folderList = drive.files().list()
                        .setQ("'"+folderID+"'"+" in parents and "+"mimeType='application/vnd.google-apps.folder'")
                        .execute();
        } catch (IOException e) {
            e.printStackTrace();
        }
        for(File folders:folderList.getFiles()){
            try {
                retSubdirFiles = findAllSubfiles(retSubdirFiles,dirpath+"\\"+folders.getName());
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }
        return retSubdirFiles;
       /* try {
            retSubdirFiles = findAllSubfiles(retSubdirFiles,dirpath);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return retSubdirFiles;
        String folderID = getFileIDfromPath(dirpath);
        FileList subdirList = null;
        try {
            subdirList = drive.files().list()
                        .setQ("'"+folderID+"'"+" in parents and "+"mimeType='application/vnd.google-apps.folder'")
                        .execute();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        FileList filesInSubFolders;
        Collection<String> subDirFilesPaths = new ArrayList<>();
        for(File subFolder:subdirList.getFiles()){
            try {
                filesInSubFolders = drive.files().list()
                                    .setQ("'"+subFolder.getId()+"'"+" in parents and "+"mimeType!='application/vnd.google-apps.folder'")
                                    .execute();
                for(File f:filesInSubFolders.getFiles()){
                    subDirFilesPaths.add("Name: "+f.getName()+"\nID:"+f.getId()+"MimeType: "+f.getMimeType()+"\nSize: "+f.getSize()+"\nMimeType: "+f.getMimeType()+"\n");
                }
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }
        return null;*/
    }

    @Override
    public Collection<String> retDirFilesAndSubdirFiles(String dirpath) throws FolderNotFoundException {
        try {
            if(!checkIfInStorage(dirpath)){
                System.out.println(getFileIDfromPath(dirpath));
                File file = drive.files().get(getFileIDfromPath(dirpath)).execute();
                System.out.println(file.getName());
                System.out.println(currentDriveStorageDirectory.getRootID());
                System.out.println(currentDriveStorageDirectory.getRoot());
                System.out.println("Path outside of Storage");
                return null;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        Collection<String> retFiles = new ArrayList<>();
        String folderID = getFileIDfromPath(dirpath);
        try {
            retFiles = findAllSubfiles(retFiles,folderID);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return retFiles;
        /*String folderID = getFileIDfromPath(dirpath);
        Collection<String> returningFiles = new ArrayList<>();
        //Creating a  FileList of subfolders
        FileList subdirList = null;
        try {
            subdirList = drive.files().list()
                    .setQ("'"+folderID+"'"+" in parents and "+"mimeType='application/vnd.google-apps.folder'")
                    .execute();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        //Adding the files withing the original folder
        FileList fileList = null;
        try {
            fileList = drive.files().list()
                    .setQ("'"+folderID+"'"+" in parents"+"mimeType!='application/vnd.google-apps.folder'")
                    .execute();
        } catch (IOException e) {
            e.printStackTrace();
        }
        for(File f: fileList.getFiles()){
            returningFiles.add(f.getId());
        }
        FileList subdirFileList = null;
        for(File folder:subdirList.getFiles()){
            try {
                subdirFileList = drive.files().list()
                        .setQ("'"+folder.getId()+"'"+" in parents"+"mimeType!='application/vnd.google-apps.folder'")
                        .execute();
                for(File subdirf: subdirFileList.getFiles()){
                    returningFiles.add(subdirf.getId());
                }
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }
        return null;*/
    }

    //NOTE dirpath is the path to Storage
    @Override
    public Collection<String> retFilesWithExtension(String dirpath, String extension) throws ForbidenExtensionException, FolderNotFoundException {
        try {
            if(!checkIfInStorage(dirpath)){
                System.out.println(getFileIDfromPath(dirpath));
                File file = drive.files().get(getFileIDfromPath(dirpath)).execute();
                File fileroot = drive.files().get(currentDriveStorageDirectory.getRootID()).execute();
                System.out.println(file.getName());
                System.out.println(file.getId());
                System.out.println(fileroot.getName());
                System.out.println(fileroot.getId());
                System.out.println(currentDriveStorageDirectory.getRootID());
                System.out.println(currentDriveStorageDirectory.getRoot());
                System.out.println("Path outside of Storage");
                return null;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        String folderID = getFileIDfromPath(dirpath);
        Collection<String> filesWithExtention = new ArrayList<>();
        Collection<String> allFiles = new ArrayList<>();
        try {
            allFiles = findAllSubfiles(allFiles,folderID);
        } catch (IOException e) {
            e.printStackTrace();
        }
        FileList fileList = null;
        try {
            fileList = drive.files().list()
                    .setQ("mimeType!='application/vnd.google-apps.folder'")
                    .execute();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        for(File f:fileList.getFiles()){
            if((f.getFileExtension().equalsIgnoreCase(extension)) && allFiles.contains("Name: "+f.getName()+";ID: "+f.getId())){
                filesWithExtention.add("Name: "+f.getName()+";ID: "+f.getId());
            }
            /*try {
                if(f.getFileExtension().equalsIgnoreCase(extension) && checkIfParent(dirpath,f.getId()))
                    filesWithExtention.add("Name: "+f.getName()+";ID: "+f.getId());
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }*/
            //filesWithExtention.add("Name: "+f.getName()+"\nID:"+f.getId()+"MimeType: "+f.getMimeType()+"\nSize: "+f.getSize()+"\nMimeType: "+f.getMimeType()+"\n");
        }
        if(filesWithExtention.isEmpty())
            filesWithExtention.add("Nema fajlova sa extenzijom: "+extension);
        return filesWithExtention;
    }

    @Override
    public Collection<String> containsString(String dirpath,String substring) throws FolderNotFoundException {
        try {
            if(!checkIfInStorage(dirpath)){
                System.out.println("Path outside of Storage");
                return null;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        String folderID = getFileIDfromPath(dirpath);
        Collection<String> filesWithSubstring = new ArrayList<>();
        Collection<String> allStorageFiles = new ArrayList<>();
        try {
            allStorageFiles = findAllSubfiles(allStorageFiles,folderID);
        } catch (IOException e) {
            e.printStackTrace();
        }
        FileList fileList = null;
        try {
            fileList = drive.files().list().execute();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        for(File f:fileList.getFiles()){
            if(f.getName().contains(substring) && allStorageFiles.contains("Name: "+f.getName()+";ID: "+f.getId()))
                filesWithSubstring.add("Name: "+f.getName()+";ID: "+f.getId());
                //filesWithSubstring.add("Name: "+f.getName()+"\nID:"+f.getId()+"MimeType: "+f.getMimeType()+"\nSize: "+f.getSize()+"\nMimeType: "+f.getMimeType()+"\n");

        }
        return filesWithSubstring;
    }

    @Override
    public boolean containsFiles(String dirPath,String... fileNames) throws FolderNotFoundException {
        try {
            if(!checkIfInStorage(dirPath)){
                System.out.println("Path outside of Storage");
                return false;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        String folderID = getFileIDfromPath(dirPath);
        Collection<String> fileNamesColelction = Arrays.asList(fileNames);
        FileList fileList = null;
        Collection<String> fileListNames = new ArrayList<>();
        try {
            fileList = drive.files().list()
                    .setQ("'"+folderID+"'"+" in parents")
                    .execute();
            for(File f:fileList.getFiles())
                fileListNames.add(f.getName());
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return fileListNames.containsAll(fileNamesColelction);
    }

    @Override
    public String parentFolderPath(String name) throws FileNotFoundException {

        FileList filelist = null;
        try {
            filelist = drive.files().list()
                    .setQ("name = "+name)
                    .execute();;
        } catch (IOException e) {
            e.printStackTrace();
        }
        for (File f:filelist.getFiles())
            return f.getParents().toString();
        return null;
    }

    @Override
    public Collection<String> selectByDateCreated(String dirpath,String startDate,String endDate) throws FolderNotFoundException {
        try {
            if(!checkIfInStorage(dirpath)){
                System.out.println("Path outside of Storage");
                return null;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        String folderID = getFileIDfromPath(dirpath);
        Collection<String> allSubfiles = new ArrayList<>();
        try {
            allSubfiles = findAllSubfiles(allSubfiles,folderID);
        } catch (IOException e) {
            e.printStackTrace();
        }
        SimpleDateFormat sampleStartDateTime = new SimpleDateFormat("dd-MM-yyyy;HH:mm:ss");
        Date startDateDate = null;
        Date endDateDate = null;
        try {
             startDateDate = sampleStartDateTime.parse(startDate);
             endDateDate = sampleStartDateTime.parse(endDate);
        } catch (ParseException e) {
            e.printStackTrace();
            return null;
        }
        FileList fileList = null;
        Collection<String> filesCreatedWithin = new ArrayList<>();
        try {
            fileList = drive.files().list()
                    .setQ("'"+folderID+"'"+" in parents")
                    .execute();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        for(File f:fileList.getFiles()){
            Date fdate = new Date(f.getCreatedTime().getValue());
            if(fdate.after(startDateDate)  && fdate.before(endDateDate) && allSubfiles.contains("Name: "+f.getName()+";ID: "+f.getId())){
                filesCreatedWithin.add(f.getId());
            }
        }
        return filesCreatedWithin;
    }

    @Override
    public Collection<String> selectByDateModified(String dirpath, String startDate, String endDate) throws FolderNotFoundException {
        try {
            if(!checkIfInStorage(dirpath)){
                System.out.println("Path outside of Storage");
                return null;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        String folderID = getFileIDfromPath(dirpath);
        Collection<String> allSubfiles = new ArrayList<>();
        try {
            allSubfiles = findAllSubfiles(allSubfiles,folderID);
        } catch (IOException e) {
            e.printStackTrace();
        }
        SimpleDateFormat sampleStartDateTime = new SimpleDateFormat("dd-MM-yyyy;HH:mm:ss");
        Date startDateDate = null;
        Date endDateDate = null;
        try {
            startDateDate = sampleStartDateTime.parse(startDate);
            endDateDate = sampleStartDateTime.parse(endDate);
        } catch (ParseException e) {
            e.printStackTrace();
            return null;
        }
        FileList fileList = null;
        Collection<String> filesModifiedWithin = new ArrayList<>();
        try {
            fileList = drive.files().list()
                    .setQ("'"+folderID+"'"+" in parents")
                    .execute();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        for(File f:fileList.getFiles()){
            Date fdate = new Date(f.getModifiedTime().getValue());
            if(fdate.after(startDateDate)  && fdate.before(endDateDate) && allSubfiles.contains("Name: "+f.getName()+";ID: "+f.getId())){
                filesModifiedWithin.add(f.getId());
            }
        }
        return filesModifiedWithin;
        /*try {
            if(!checkIfInStorage(dirpath)){
                System.out.println("Path outside of Storage");
                return null;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        SimpleDateFormat sampleStartDateTime = new SimpleDateFormat("dd-MM-yyyy;HH:mm:ss");
        Date startDateDate = null;
        Date endDateDate = null;
        try {
            startDateDate = sampleStartDateTime.parse(startDate);
            endDateDate = sampleStartDateTime.parse(endDate);
        } catch (ParseException e) {
            e.printStackTrace();
            return null;
        }
        String folderID = getFileIDfromPath(dirpath);
        FileList fileList = null;
        Collection<String> filesModifiedWithin = new ArrayList<>();
        try {
            fileList = drive.files().list()
                    .setQ("'"+folderID+"'"+" in parents")
                    .execute();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        for(File f:fileList.getFiles()){
            Date fdate = new Date(f.getModifiedTime().getValue());
            if(fdate.after(startDateDate)  && fdate.before(endDateDate)){
                filesModifiedWithin.add(f.getId());
            }
        }
        return filesModifiedWithin;*/
    }
    /* FOR TESTING */
    public static void main(String[] args) throws IOException {

        Drive service = getDriveService();
        FileList result = service.files().list()
                .setPageSize(10)
                .setFields("nextPageToken, files(id, name)")
                .execute();
        List<File> files = result.getFiles();
        if (files == null || files.isEmpty()) {
            System.out.println("No files found.");
        } else {
            System.out.println("Files:");
            for (File file : files) {
                System.out.printf("%s (%s)\n", file.getName(), file.getId());
            }
        }
    }
}
