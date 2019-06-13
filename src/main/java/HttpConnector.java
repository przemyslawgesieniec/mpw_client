import org.apache.http.NameValuePair;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.stream.Collectors;

public class HttpConnector {

    private static HttpURLConnection connection;
    private String serverUrl;

    public HttpConnector(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    public String httpGet(final String serverEndpoint, List<NameValuePair> params) throws IOException {

        String endpoint = serverUrl + "/" + serverEndpoint;

        if (!params.isEmpty()) {
            final String requestParams = formatRequestParams(params);
            endpoint += requestParams.replaceAll(" ","%20");
        }

        try {

            URL url = new URL(endpoint);
            connection = (HttpURLConnection) url.openConnection();
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

    private String formatRequestParams(List<NameValuePair> params) {
        return "?" + params
                .stream()
                .map(e -> e.getName() + "=" + e.getValue())
                .collect(Collectors.joining("&"));
    }


}
