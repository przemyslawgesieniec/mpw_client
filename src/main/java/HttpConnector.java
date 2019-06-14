import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.stream.Collectors;

public class HttpConnector {

    private String serverUrl;

    public HttpConnector(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    public String httpGet(final String serverEndpoint, List<NameValuePair> params) throws IOException {

        HttpURLConnection connection = null;
        String endpoint = serverUrl + "/" + serverEndpoint;

        if (!params.isEmpty()) {
            final String requestParams = formatRequestParams(params);
            endpoint += requestParams.replaceAll(" ", "%20");
        }

        URL url = new URL(endpoint);
        connection = (HttpURLConnection) url.openConnection();

        try {
            connection.setRequestMethod("GET");

            StringBuilder content;

            try (BufferedReader in = new BufferedReader(
                    new InputStreamReader(connection.getInputStream()))) {

                String line;
                content = new StringBuilder();

                while ((line = in.readLine()) != null) {
                    content.append(line);
                    content.append(System.lineSeparator());
                }
            }

            return content.toString();

        } finally {

            connection.disconnect();
        }

    }

    public String htttPostFile(List<File> files, String user) throws IOException {

        CloseableHttpClient client = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost(serverUrl + "/files/upload");

        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        builder.addTextBody("user", user);
        files.forEach(e -> builder.addBinaryBody("files", e, ContentType.APPLICATION_OCTET_STREAM, e.getName()));

        HttpEntity multipart = builder.build();
        httpPost.setEntity(multipart);

        CloseableHttpResponse response = client.execute(httpPost);

        StringBuilder content;

        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(response.getEntity().getContent()))) {

            String line;
            content = new StringBuilder();

            while ((line = in.readLine()) != null) {
                content.append(line);
                content.append(System.lineSeparator());
            }
        }
        client.close();

        return content.toString();

    }

    private String formatRequestParams(List<NameValuePair> params) {
        return "?" + params
                .stream()
                .map(e -> e.getName() + "=" + e.getValue())
                .collect(Collectors.joining("&"));
    }


}
