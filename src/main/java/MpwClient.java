import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MpwClient {

    private String userName;
    private String userDirectoryPath;
    private String userCsvFilePath;
    private MpwServerAdapter mpwServerClient;

    public MpwClient(String userName) {
        this.userName = userName;
        String userDirName = userName + "Directory";
        String rootDirectoryPath = "src/main/resources/usersLocalDirectories";
        userDirectoryPath = rootDirectoryPath + "/" + userDirName;
        userCsvFilePath = userDirectoryPath + "/c.csv";
        mpwServerClient = new MpwServerAdapter();
    }

    public void start() throws IOException, InterruptedException, URISyntaxException {

        initializeDirectoryIfNecessary();

        final List<String> filesToDownload = synchronizeWithServer();

        requestDownloadingMissingFiles(filesToDownload);
        downloadMissingFiles(filesToDownload);

        newFileListener();
    }

    public List<String> synchronizeWithServer() throws IOException, URISyntaxException {

        final Map<String, String> remoteStoredServerAndLocalFilesName = mpwServerClient.getRemoteStoredUserFilesName(userName);
        remoteStoredServerAndLocalFilesName.forEach((k, v) -> System.out.println(userName + ": missing " + v + " file"));

        final List<File> allFilesFromLocalDirectory = getLocalFiles();
        final List<String> allFilesNamesFromLocalDirectory = allFilesFromLocalDirectory
                .stream()
                .map(File::getName)
                .collect(Collectors.toList());

        return filterFilesToDownload(remoteStoredServerAndLocalFilesName, allFilesNamesFromLocalDirectory);

    }

    private void uploadNewFiles() throws IOException {

        final List<File> allFilesFromLocalDirectory = getLocalFiles();
        final List<File> filesToUpload = filesToUpload(allFilesFromLocalDirectory);

        final Map<String, String> remoteAndLocalFilesNames = mpwServerClient.uploadFies(filesToUpload, userName);

        remoteAndLocalFilesNames.forEach((remoteFileName, localFileName) -> {
            System.out.println(userName + " has uploaded file: " + localFileName);
            updateCsvFile(localFileName, remoteFileName);
        });
    }


    private void downloadMissingFiles(final List<String> filesToDownload) {

        final List<String> downloadedFilesData = new ArrayList<>();
            while (downloadedFilesData.size() < filesToDownload.size()) {
                final List<UserFileData> downloadResponse = mpwServerClient.downloadFiles(filesToDownload);
                downloadedFilesData.addAll(saveFiles(downloadResponse));
            }
    }

    private List<String> saveFiles(List<UserFileData> downloadResponse) {

        final List<String> downloadedFilesData = new ArrayList<>();
        downloadResponse.forEach(d -> {
            saveFile(d.getOriginalFileName(), d.getServerFileName(), d.getContent());
            System.out.println(userName + ": file " + d.getOriginalFileName() + " downloaded");
            downloadedFilesData.add(d.getServerFileName());
        });
        return downloadedFilesData;
    }

    private void requestDownloadingMissingFiles(List<String> filesToDownload) throws IOException, URISyntaxException {

        mpwServerClient.requestFilesDownload(filesToDownload, userName);
    }

    private List<String> filterFilesToDownload(Map<String, String> remoteStoredServerAndLocalFilesName, List<String> allFilesFromLocalDirectory) {

        return remoteStoredServerAndLocalFilesName
                .entrySet()
                .stream()
                .filter(f -> !allFilesFromLocalDirectory.contains(f.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private List<File> getLocalFiles() {

        File directory = new File(userDirectoryPath);
        final List<File> collect = Stream
                .of(Objects.requireNonNull(directory.listFiles()))
                .collect(Collectors.toList());

        return collect
                .stream()
                .filter(e -> !e.getName().equals("c.csv"))
                .collect(Collectors.toList());
    }

    private void initializeDirectoryIfNecessary() {

        Path directory = Paths.get(userDirectoryPath);
        if (!Files.exists(directory)) {
            System.out.println(userName + ": No local directory");
            try {
                Files.createDirectories(directory);
                createCsvFile();
                System.out.println(userName + ": Local directory created");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void createCsvFile() throws IOException {
        Path file = Paths.get(userCsvFilePath);
        Files.write(file, new ArrayList<>(), StandardCharsets.UTF_8);
    }

    private List<File> filesToUpload(List<File> allFilesFromLocalDirectory) {

        final List<String> allUploadedFilesNamesFormCsv = getAllUploadedFilesNameFormCsv();

        return allFilesFromLocalDirectory
                .stream()
                .filter(file -> !allUploadedFilesNamesFormCsv.contains(file.getName()))
                .collect(Collectors.toList());
    }

    private void saveFile(String originalFileName, final String serverFileName, final String fileContent) {

        Path filepath = Paths.get(userDirectoryPath + "/" + originalFileName);

        try (BufferedWriter writer = Files.newBufferedWriter(filepath)) {
            writer.write(fileContent);
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }

        updateCsvFile(originalFileName, serverFileName);
    }

    private synchronized void updateCsvFile(final String originalFileName, final String serverFileName) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(userCsvFilePath, true))) {
            writer.append(originalFileName).append(",").append(serverFileName);
            writer.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private synchronized List<String> getAllUploadedFilesNameFormCsv() {

        final List<String> localFileNamesUploadedOnServer = new ArrayList<>();

        try (Stream<String> stream = Files.lines(Paths.get(userCsvFilePath))) {

            final List<String> collect = stream
                    .map(MpwClient::getFileName)
                    .distinct()
                    .collect(Collectors.toList());

            localFileNamesUploadedOnServer.addAll(collect);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return localFileNamesUploadedOnServer;
    }

    private static String getFileName(String e) {
        return Arrays.asList(e.split(",")).get(0);
    }

    private void newFileListener() throws IOException, InterruptedException {
        while (true) {
            Thread.sleep(5000);
            uploadNewFiles();
        }
    }
}
