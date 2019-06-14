import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import model.UserFileData;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DropBoxClient {

    private String clientName;
    private String localDirectoryName;

    private String ROOT_DIRECTORY_PATH = "src/main/resources/usersLocalDirectories";
    private String USER_DIRECTORY_PATH;
    private String USER_CSV_FILE_PATH;

    private static String SYNC = "sync";
    private static String REQUEST_DOWNLOAD = "request/download";
    private static String DOWNLOAD = "download";

    public DropBoxClient(String clientName) {
        this.clientName = clientName;
        this.localDirectoryName = clientName + "Directory";

        USER_DIRECTORY_PATH = ROOT_DIRECTORY_PATH + "/" + localDirectoryName;
        USER_CSV_FILE_PATH = USER_DIRECTORY_PATH + "/c.csv";
    }

    public void runClient() throws IOException, InterruptedException {

        initializeDirectoryIfNecessary();

        final Map<String, String> remoteStoredServerAndLocalFilesName = getRemoteStoredFilesName();
        final List<File> allFilesFromLocalDirectory = getAllFilesFromLocalDirectoryExceptCsv();
        final List<String> allFilesNamesFromLocalDirectory = allFilesFromLocalDirectory
                .stream()
                .map(File::getName)
                .collect(Collectors.toList());

        final List<String> filesToDownload = filesToDownload(remoteStoredServerAndLocalFilesName, allFilesNamesFromLocalDirectory);

        requestDownloadingMissingFiles(filesToDownload);
        downloadAllMissingFilesAsync(filesToDownload);


        while (true) {
            Thread.sleep(5000);
            uploadNewFiles();
        }
    }

    private void uploadNewFiles() throws IOException {

        final List<File> allFilesFromLocalDirectory = getAllFilesFromLocalDirectoryExceptCsv();
        final List<File> files = filesToUpload(allFilesFromLocalDirectory);

        if (files.size() > 0) {
            files.forEach(e -> System.out.println(e.getName() + " ready to upload"));
            final String uploadResponse = new HttpConnector("http://localhost:8080").htttPostFile(files, clientName);
            final List<String> remotelyUploadedFiles = getList(uploadResponse, String.class);
            final Map<String, String> remoteAndLocalFilesNames = remotelyStoredFilesNamesToMap(remotelyUploadedFiles);
            remoteAndLocalFilesNames.forEach((remote, local) -> {
                System.out.println(clientName + " has uploaded file: " + local);
                updateCsvFile(local, remote);
            });

        }
    }

    private void downloadAllMissingFilesAsync(final List<String> filesToDownload) {

        final List<String> downloadedFilesData = new ArrayList<>();
        new Thread(() -> {
            while (downloadedFilesData.size() < filesToDownload.size()) {

                final List<NameValuePair> params = new ArrayList<>();
                final String filedToDownloadComaSeparated = String.join(",", filesToDownload);

                params.add(new BasicNameValuePair("filesNames", filedToDownloadComaSeparated));
                try {
                    final String response = new HttpConnector("http://localhost:8080").httpGet(DOWNLOAD, params);
                    final List<UserFileData> downloadResponse = parseDownloadResponse(response);
                    downloadResponse.forEach(d -> {
                        saveFile(d.getOriginalFileName(), d.getServerFileName(), d.getContent());
                        System.out.println(clientName + ": file " + d.getOriginalFileName() + " downloaded");
                        downloadedFilesData.add(d.getServerFileName());
                    });
                    Thread.sleep(1000); //todo to remove
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private boolean requestDownloadingMissingFiles(List<String> filesToDownload) throws IOException {

        final List<NameValuePair> params = new ArrayList<>();
        final String filedToDownloadComaSeparated = String.join(",", filesToDownload);

        params.add(new BasicNameValuePair("user", clientName));
        params.add(new BasicNameValuePair("filesNames", filedToDownloadComaSeparated));

        final String response = new HttpConnector("http://localhost:8080").httpGet(REQUEST_DOWNLOAD, params);
        return response.equals("processing");
    }

    private List<String> filesToDownload(Map<String, String> remoteStoredServerAndLocalFilesName, List<String> allFilesFromLocalDirectory) {

        return remoteStoredServerAndLocalFilesName
                .entrySet()
                .stream()
                .filter(f -> !allFilesFromLocalDirectory.contains(f.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private Map<String, String> getRemoteStoredFilesName() throws IOException {

        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("user", clientName));
        final String s = new HttpConnector("http://localhost:8080").httpGet(SYNC, params);
        final Map<String, String> localAndRemoteFileName = parseSyncResponse(s);
        localAndRemoteFileName.forEach((k, v) -> System.out.println(clientName + ": " + v));
        return localAndRemoteFileName;
    }

    private List<File> getAllFilesFromLocalDirectoryExceptCsv() {

        File directory = new File(USER_DIRECTORY_PATH);
        final List<File> collect = Stream.of(Objects.requireNonNull(directory.listFiles()))
                .collect(Collectors.toList());
        return collect.stream().filter(e -> !e.getName().equals("c.csv")).collect(Collectors.toList());
    }

    private void initializeDirectoryIfNecessary() {

        Path directory = Paths.get(USER_DIRECTORY_PATH);
        if (!Files.exists(directory)) {
            try {
                Files.createDirectories(directory);
                createCsvFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        System.out.println(clientName + ": Local directory created");
    }

    private void createCsvFile() throws IOException {
        Path file = Paths.get(USER_CSV_FILE_PATH);
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

        Path filepath = Paths.get(USER_DIRECTORY_PATH + "/" + originalFileName);

        try (BufferedWriter writer = Files.newBufferedWriter(filepath)) {
            writer.write(fileContent);
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }

        updateCsvFile(originalFileName, serverFileName);
    }

    private Map<String, String> parseSyncResponse(final String response) {

        final List<String> remotelyStoredFileNames = getList(response, String.class);
        final Map<String, String> collect = remotelyStoredFilesNamesToMap(remotelyStoredFileNames);
        return collect;
    }

    private Map<String, String> remotelyStoredFilesNamesToMap(final List<String> remoteFilesNames) {
        final int prefixLength = UUID.randomUUID().toString().length();
        return remoteFilesNames
                .stream()
                .collect(Collectors.toMap(k -> k, v -> v.substring(prefixLength)));
    }

    private List<UserFileData> parseDownloadResponse(final String response) {
        return getList(response, UserFileData.class);
    }

    private <T> List<T> getList(String jsonArray, Class<T> clazz) {
        Type typeOfT = TypeToken.getParameterized(List.class, clazz).getType();
        return new Gson().fromJson(jsonArray, typeOfT);
    }

    private synchronized void updateCsvFile(final String originalFileName, final String serverFileName) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(USER_CSV_FILE_PATH, true))) {
            writer.append(originalFileName).append(",").append(serverFileName);
            writer.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private synchronized List<String> getAllUploadedFilesNameFormCsv() {

        final List<String> localFileNamesUploadedOnServer = new ArrayList<>();

        try (Stream<String> stream = Files.lines(Paths.get(USER_CSV_FILE_PATH))) {

            final List<String> collect = stream
                    .map(DropBoxClient::getFileName)
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
}
