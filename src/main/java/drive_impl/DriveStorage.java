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
                    .setPageSize(10)
                    .setFields("files(id, name,size)")
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
        List<String> parents = new ArrayList<>();
        StringTokenizer tokenizer = new StringTokenizer(path, "\\");
        while (tokenizer.hasMoreElements()) {
            parents.add(tokenizer.nextToken());
        }
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
                folderID = getID(folderName);
                continue;
            }
            try {
                FileList containsList = drive.files().list()
                        .setQ(folderID+" in parent")
                        .execute();
                if(!containsList.isEmpty()) {
                    for (File f : containsList.getFiles()) {
                        if (f.getName().equals(folderName))
                            folderID = getID(folderName);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return folderID;
    }
    //
    // Implementation of abstract class methods
    @Override
    public void saveConfig(Cfg cfg) {

    }

    @Override
    public void createStorage(String rootName) throws FolderNotFoundException {
        try {
            drive = getDriveService();
        } catch (IOException e) {
            e.printStackTrace();
        }
        File fileMetadata = new File();
        fileMetadata.setName(rootName);
        fileMetadata.setMimeType("application/vnd.google-apps.folder");
        fileMetadata.setDescription("Implemented Drive Storage");
        try {
            File file = drive.files().create(fileMetadata)
                    .setFields("id")
                    .execute();
            System.out.println("Folder ID: " + file.getId());
            currentDriveStorageDirectory = new DriveStorageDirectory();
            currentDriveStorageDirectory.setRootID(file.getId());
            //currentDriveStorageDirectory.setRoot(); // <-- need to set root properly
            String downloadsDir = System.getProperty("user.home")+"/Downloads/";
            currentDriveStorageDirectory.setDownloadFolder(downloadsDir);
        } catch (GoogleJsonResponseException e) {
            // TODO(developer) - handle error appropriately
            System.err.println("Unable to create folder: " + e.getDetails());
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("Storage Created at "+ currentDriveStorageDirectory.getRoot()); // <-- Root still needs to be set
    }

    //implement later when cfg is understood
    @Override
    public void createStorage(String rootName, String config) throws FileNotFoundException {

    }

    @Override
    public void createDirectories(String path, String... dirNames) throws FolderNotFoundException, UnsupportedOperationException {
        String folderID = getID(path);
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
    public void deleteFiles(String... files) throws FileNotFoundException, UnsupportedOperationException {
        String fileID;
        for (String s : files) {
            fileID = getID(s);
            File fileMetadata = null;
            try {
                fileMetadata = drive.files().get(fileID).execute();
            } catch (IOException e) {
                e.printStackTrace();
            }

            if(!fileMetadata.getMimeType().equalsIgnoreCase("application/vnd.google-apps.folder")) {
                try {
                    drive.files().delete(fileID);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
    @Override
    public void deleteDirectories(String... folders) throws FolderNotFoundException, UnsupportedOperationException {
        String fileID;
        for (String s : folders) {
            fileID = getID(s);
            File fileMetadata = null;
            try {
                fileMetadata = drive.files().get(fileID).execute();
            } catch (IOException e) {
                e.printStackTrace();
            }

            if(fileMetadata.getMimeType().equalsIgnoreCase("application/vnd.google-apps.folder")) {
                try {
                    drive.files().delete(fileID);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void moveFile(String source, String destination) throws FileNotFoundException, FolderNotFoundException, MaxStorageSizeException, MaxNumberOfFilesExceededException, UnsupportedOperationException {
        String destFolderID = getID(destination);
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
        if(currentDriveStorageDirectory.isMaxNumberOfFilesSet()){
            if(currentDriveStorageDirectory.getMaxNumberOfFiles() < fileList.size()){
                String sourceFileID = getID(source);
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
            }else{
                throw new MaxNumberOfFilesExceededException();
            }
        }
    }

    //TODO destination String not used
    @Override
    public void downloadFile(String source, String destination) throws FileNotFoundException, FolderNotFoundException, UnsupportedOperationException {
        String fileID = getFileIDfromPath(source);
        OutputStream outputStream = new ByteArrayOutputStream();
        try {
            drive.files().get(fileID)
                    .executeMediaAndDownloadTo(outputStream);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @Override
    public void renameFile(String path, String fileName) throws FileNotFoundException, FolderNotFoundException, InvalidNameException, UnsupportedOperationException {
        String fileID = getFileIDfromPath(path);
        File file = null;
        try {
            file = drive.files().get(fileID).execute();
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        file.setName(fileName);
        try {
            drive.files().update(fileID,file)
                    .execute();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @Override
    public Collection<String> retFiles(String dirpath) throws FolderNotFoundException {
        String folderID = getFileIDfromPath(dirpath);
        FileList fileList = null;
        try {
            fileList = drive.files().list()
                    .setQ(folderID+" in parents")
                    .execute();
        } catch (IOException e) {
            e.printStackTrace();
        }
        Collection<String> retFiles = new ArrayList<>();
        for(File f:fileList.getFiles()){
            retFiles.add("Name: "+f.getName()+"\nID:"+f.getId()+"MimeType: "+f.getMimeType()+"\nSize: "+f.getSize()+"\nMimeType: "+f.getMimeType()+"\n");
        }
        return retFiles;
    }

    @Override
    public Collection<String> retSubdirFiles(String dirpath) throws FolderNotFoundException {
        String folderID = getFileIDfromPath(dirpath);
        FileList subdirList = null;
        try {
            subdirList = drive.files().list()
                        .setQ(folderID+" in parents and "+"mimeType='application/vnd.google-apps.folder'")
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
                                    .setQ(subFolder.getId()+" in parents and "+"mimeType!='application/vnd.google-apps.folder'")
                                    .execute();
                for(File f:filesInSubFolders.getFiles()){
                    subDirFilesPaths.add("Name: "+f.getName()+"\nID:"+f.getId()+"MimeType: "+f.getMimeType()+"\nSize: "+f.getSize()+"\nMimeType: "+f.getMimeType()+"\n");
                }
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }
        return null;
    }

    @Override
    public Collection<String> retDirFilesAndSubdirFiles(String dirpath) throws FolderNotFoundException {
        String folderID = getFileIDfromPath(dirpath);
        Collection<String> returningFiles = new ArrayList<>();
        //Creating a  FileList of subfolders
        FileList subdirList = null;
        try {
            subdirList = drive.files().list()
                    .setQ(folderID+" in parents and "+"mimeType='application/vnd.google-apps.folder'")
                    .execute();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        //Adding the files withing the original folder
        FileList fileList = null;
        try {
            fileList = drive.files().list()
                    .setQ(folderID+" in parents"+"mimeType!='application/vnd.google-apps.folder'")
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
                        .setQ(folder.getId()+" in parents"+"mimeType!='application/vnd.google-apps.folder'")
                        .execute();
                for(File subdirf: subdirFileList.getFiles()){
                    returningFiles.add(subdirf.getId());
                }
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }






        return null;
    }

    //TODO figure out how to deal with extensions + Pretraga nad SLKADISTEM, ne zadatim direktorijum => dirpath is reduntandt
    @Override
    public Collection<String> retFilesWithExtension(String dirpath, String extension) throws ForbidenExtensionException, FolderNotFoundException {
        String folderID = getFileIDfromPath(dirpath);
        Collection<String> filesWithExtention = new ArrayList<>();
        FileList fileList = null;
        try {
            fileList = drive.files().list().execute();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        for(File f:fileList.getFiles()){
            if(f.getFileExtension().equals(extension))
                filesWithExtention.add("Name: "+f.getName()+"\nID:"+f.getId()+"MimeType: "+f.getMimeType()+"\nSize: "+f.getSize()+"\nMimeType: "+f.getMimeType()+"\n");
        }
        return filesWithExtention;
    }

    //TODO Pretraga nad SLKADISTEM, ne zadatim direktorijum => dirpath is reduntandt
    @Override
    public Collection<String> containsString(String dirpath,String substring) throws FolderNotFoundException {
        String folderID = getFileIDfromPath(dirpath);
        Collection<String> filesWithSubstring = new ArrayList<>();
        FileList fileList = null;
        try {
            fileList = drive.files().list().execute();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        for(File f:fileList.getFiles()){
            if(f.getName().contains(substring))
                filesWithSubstring.add("Name: "+f.getName()+"\nID:"+f.getId()+"MimeType: "+f.getMimeType()+"\nSize: "+f.getSize()+"\nMimeType: "+f.getMimeType()+"\n");
        }
        return null;
    }

    @Override
    public boolean containsFiles(String dirPath,String... fileNames) throws FolderNotFoundException {
        String folderID = getFileIDfromPath(dirPath);
        Collection<String> fileNamesColelction = Arrays.asList(fileNames);
        FileList fileList = null;
        Collection<String> fileListNames = new ArrayList<>();
        try {
            fileList = drive.files().list()
                    .setQ(folderID+" in parents")
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
    public String parentFolderPath(String filePath) throws FileNotFoundException {
        String fileID = getFileIDfromPath(filePath);
        File file = null;
        try {
            file = drive.files().get(fileID).execute();
            return file.getParents().toString();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public Collection<String> selectByDateCreated(String dirpath,String startDate,String endDate) throws FolderNotFoundException {
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
        Collection<String> filesCreatedWithin = new ArrayList<>();
        try {
            fileList = drive.files().list()
                    .setQ(folderID+" in parents")
                    .execute();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        for(File f:fileList.getFiles()){
            Date fdate = new Date(f.getCreatedTime().getValue());
            if(fdate.after(startDateDate)  && fdate.before(endDateDate)){
                filesCreatedWithin.add(f.getId());
            }
        }
        return filesCreatedWithin;
    }

    @Override
    public Collection<String> selectByDateModified(String dirpath, String startDate, String endDate) throws FolderNotFoundException {
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
                    .setQ(folderID+" in parents")
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
        return filesModifiedWithin;
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
