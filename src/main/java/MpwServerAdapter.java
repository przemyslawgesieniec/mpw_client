import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class MpwServerAdapter {

    private static final String BULK_UPLOAD = "files/upload";
    private static final String DOWNLOAD = "download";
    private static final String SYNCHRONIZE = "sync";
    private static final String REQUEST_DOWNLOAD = "request/download";
    private final HttpClientAdapter httpClient = new HttpClientAdapter("http://localhost:8080");



    public Map<String, String> getRemoteStoredUserFilesName(String userName) throws IOException, URISyntaxException {

        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("user", userName));
        final String s = new HttpClientAdapter("http://localhost:8080").httpGet(SYNCHRONIZE, params);
        final Map<String, String> localAndRemoteFileName = parseSyncResponse(s);
        return localAndRemoteFileName;
    }

    public Map<String, String> uploadFies(List<File> filesToUpload, String userName) throws IOException {

        if (filesToUpload.size() > 0) {
            filesToUpload.forEach(e -> System.out.println(e.getName() + " ready to upload"));
            final String uploadResponse = new HttpClientAdapter("http://localhost:8080").httpPostFile(BULK_UPLOAD, filesToUpload, userName);
            final List<String> remotelyUploadedFiles = getList(uploadResponse, String.class);
            return remotelyStoredFilesNamesToMap(remotelyUploadedFiles);
        }
        return new HashMap<>();
    }

    public void requestFilesDownload(List<String> filesToDownload, String userName) throws IOException, URISyntaxException {
        final List<NameValuePair> params = new ArrayList<>();
        final String filedToDownloadComaSeparated = String.join(",", filesToDownload);

        params.add(new BasicNameValuePair("user", userName));
        params.add(new BasicNameValuePair("filesNames", filedToDownloadComaSeparated));

        new HttpClientAdapter("http://localhost:8080").httpGet(REQUEST_DOWNLOAD, params);
    }


    public List<UserFileData> downloadFiles(List<String> filesToDownload) {

        final List<NameValuePair> params = new ArrayList<>();
        final String filesToDownloadComaSeparated = String.join(",", filesToDownload);
        try {
            params.add(new BasicNameValuePair("filesNames", filesToDownloadComaSeparated));
            final String response = httpClient.httpGet(DOWNLOAD, params);
            return parseDownloadResponse(response);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new ArrayList<>();
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
}
