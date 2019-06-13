import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import java.io.File;
import java.io.IOException;
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


    private static String ROOT_DIRECTORY_PATH = "src/main/resources/usersLocalDirectories";
    private static String USER_DIRECTORY_PATH;
    private static String USER_CSV_FILE_PATH;

    private static String SYNC = "sync";
    private static String REQUEST_DOWNLOAD = "request/download";

    public DropBoxClient(String clientName) {
        this.clientName = clientName;
        this.localDirectoryName = clientName + "Directory";

        USER_DIRECTORY_PATH = ROOT_DIRECTORY_PATH + "/" + localDirectoryName;
        USER_CSV_FILE_PATH = USER_DIRECTORY_PATH + "/c.csv";
        httpConnector = new HttpConnector("http://localhost:8080");
    }

    public void runClient() throws IOException {

        initializeDirectoryIfNecessary();

        final List<String> remoteStoredFilesName = getRemoteStoredFilesName();
        final List<File> allFilesFromLocalDirectory = getAllFilesFromLocalDirectory();
        final List<String> filesToDownload = filesToDownload(remoteStoredFilesName, allFilesFromLocalDirectory);

        requestDownloadingMissingFiles(filesToDownload);

        //todo in loop get all files from server

        //todo in loop scan local dir for changes, and send the to the server
    }

    private void requestDownloadingMissingFiles(List<String> filesToDownload) throws IOException {

        final List<NameValuePair> params = new ArrayList<>();
        final String filedToDownloadComaSeparated = String.join(",", filesToDownload);

        params.add(new BasicNameValuePair("user", clientName));
        params.add(new BasicNameValuePair("filesNames", filedToDownloadComaSeparated));

        final String response = httpConnector.httpGet(REQUEST_DOWNLOAD, params);
        System.out.println(response);
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


    private List<File> filesToBeUploaded(List<String> remoteStoredFilesName, List<File> allFilesFromLocalDirectory) {

        return allFilesFromLocalDirectory
                .stream()
                .filter(file -> !remoteStoredFilesName.contains(file.getName()))
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
}
