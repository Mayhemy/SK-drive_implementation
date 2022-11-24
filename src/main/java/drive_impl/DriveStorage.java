package drive_impl;

import Exceptions.*;
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
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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
    public void loadFiles(String path,String... fileNames) throws FileNotFoundException, MaxStorageSizeException, MaxNumberOfFilesExceededException, UnsupportedOperationException {

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

    @Override
    public void downloadFile(String source, String destination) throws FileNotFoundException, FolderNotFoundException, UnsupportedOperationException {

    }

    @Override
    public void renameFile(String s, String s1) throws FileNotFoundException, FolderNotFoundException, InvalidNameException, UnsupportedOperationException {

    }

    @Override
    public Collection<String> retFiles(String dirpath) throws FolderNotFoundException {
        List<File> files = new ArrayList<File>();
        Collection<String> stringListOfFiles = new ArrayList<>();

        String fileID = getID(dirpath);

        try {
            File searchedFolder = drive.files().get(dirpath).execute();
        } catch (IOException e) {
            e.printStackTrace();
        }
        FileList result = null;
        try {
            result = drive.files().list()
                    .setQ(fileID+ " in parents")
                    .execute();
        } catch (IOException e) {
            e.printStackTrace();
        }
        for (File file : result.getFiles()) {
            stringListOfFiles.add(file.getId());
        }

        return stringListOfFiles;
    }

    @Override
    public Collection<String> retSubdirFiles(String s) throws FolderNotFoundException {
        return null;
    }

    @Override
    public Collection<String> retDirFilesAndSubdirFiles(String s) throws FolderNotFoundException {
        return null;
    }

    @Override
    public Collection<String> retFilesWithExtension(String s, String s1) throws ForbidenExtensionException, FolderNotFoundException {
        return null;
    }

    @Override
    public Collection<String> containsString(String s, String s1) throws FolderNotFoundException {
        return null;
    }

    @Override
    public boolean containsFiles(String s, String... strings) throws FolderNotFoundException {
        return false;
    }

    @Override
    public String parentFolderPath(String s) throws FileNotFoundException {
        return null;
    }

    @Override
    public Collection<String> selectByDateCreated(String s, String s1, String s2) throws FolderNotFoundException {
        return null;
    }

    @Override
    public Collection<String> selectByDateModified(String s, String s1, String s2) throws FolderNotFoundException {
        return null;
    }
    /* FOR TESTING*/
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
