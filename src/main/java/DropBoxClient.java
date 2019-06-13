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
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DropBoxClient {

    private String clientName;
    private String localDirectoryName;
    private HttpConnector httpConnector;


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
        httpConnector = new HttpConnector("http://localhost:8080");
    }

    public void runClient() throws IOException, InterruptedException {

        initializeDirectoryIfNecessary();

        final List<String> remoteStoredFilesName = getRemoteStoredFilesName();
        final List<File> allFilesFromLocalDirectory = getAllFilesFromLocalDirectory();

        final List<String> filesToDownload = filesToDownload(remoteStoredFilesName, allFilesFromLocalDirectory);

        final boolean isRequested = requestDownloadingMissingFiles(filesToDownload);

        downloadAllMissingFilesAsync(filesToDownload);


        while (true) {
            Thread.sleep(5000);
//            uploadNewFiles();
        }

    }
.
    private void uploadNewFiles() throws IOException {

        final List<File> allFilesFromLocalDirectory = getAllFilesFromLocalDirectory();
        final List<File> files = filesToUpload(allFilesFromLocalDirectory);

//        httpConnector.httpPost();


        //todo get feedback from server if file was uploaded!!!!!

    }

    private void downloadAllMissingFilesAsync(final List<String> filesToDownload) {

        final List<String> downloadedFilesData = new ArrayList<>();
        new Thread(() -> {
            while (downloadedFilesData.size() < filesToDownload.size()) {

                final List<NameValuePair> params = new ArrayList<>();
                final String filedToDownloadComaSeparated = String.join(",", filesToDownload);

                params.add(new BasicNameValuePair("filesNames", filedToDownloadComaSeparated));
                try {
                    final String response = httpConnector.httpGet(DOWNLOAD, params);
                    final List<UserFileData> downloadResponse = parseDownloadResponse(response);
                    downloadResponse.forEach(d -> {
                        saveFile(d.getOriginalFileName(),d.getServerFileName(), d.getContent());
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

        final String response = httpConnector.httpGet(REQUEST_DOWNLOAD, params);
        return response.equals("processing");
    }

    private List<String> filesToDownload(List<String> remoteStoredFilesName, List<File> allFilesFromLocalDirectory) {

        final List<String> allFilesFromLocalDirectoryNames = allFilesFromLocalDirectory
                .stream()
                .map(File::getName)
                .collect(Collectors.toList());

        return remoteStoredFilesName
                .stream()
                .filter(f -> !allFilesFromLocalDirectoryNames.contains(f))
                .collect(Collectors.toList());
    }

    private List<String> getRemoteStoredFilesName() throws IOException {

        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("user", clientName));
        final String s = httpConnector.httpGet(SYNC, params);
        System.out.println(s);
        return Arrays.asList(formatResponseString(s).split(","));
    }

    private List<File> getAllFilesFromLocalDirectory() {

        File directory = new File(USER_DIRECTORY_PATH);
        return Stream.of(Objects.requireNonNull(directory.listFiles()))
                .collect(Collectors.toList());
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

    private String formatResponseString(final String responseString) {
        return responseString
                .replaceAll("\"", "")
                .replaceAll("\\[", "")
                .replaceAll("]", "")
                .replaceAll("\\n", "")
                .replaceAll("\\r", "");

    }

    private void saveFile(String originalFileName, final String serverFileName, final String fileContent) {

        Path filepath = Paths.get(USER_DIRECTORY_PATH + "/" + originalFileName);

        try (BufferedWriter writer = Files.newBufferedWriter(filepath)) {
            writer.write(fileContent);
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }

        updateCsvFile(originalFileName,serverFileName);
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

    private synchronized List<String> getAllUploadedFilesNameFormCsv(){

        final List<String> localFileNamesUploadedOnServer = new ArrayList<>();

            try (Stream<String> stream = Files.lines(Paths.get(USER_CSV_FILE_PATH))) {

                final List<String> collect = stream
                        .map(DropBoxClient::getFileName)
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
