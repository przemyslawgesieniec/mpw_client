import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class HttpConnector {

//    private HttpURLConnection connection;
    private String serverUrl;

    public HttpConnector(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    public String httpGet(final String serverEndpoint, List<NameValuePair> params) throws IOException {

        HttpURLConnection connection = null;
        String endpoint = serverUrl + "/" + serverEndpoint;

        if (!params.isEmpty()) {
            final String requestParams = formatRequestParams(params);
            endpoint += requestParams.replaceAll(" ","%20");
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

    private String formatRequestParams(List<NameValuePair> params) {
        return "?" + params
                .stream()
                .map(e -> e.getName() + "=" + e.getValue())
                .collect(Collectors.joining("&"));
    }


//    public String httpPost(final String serverEndpoint, List<NameValuePair> params) throws IOException {
//
//        HttpURLConnection connection = (HttpURLConnection) new URL(serverUrl).openConnection();
//        String endpoint = serverUrl + "/" + serverEndpoint;
//
//        if (!params.isEmpty()) {
//            final String requestParams = formatRequestParams(params);
//            endpoint += requestParams.replaceAll(" ","%20");
//        }
//
//        URL url = new URL(endpoint);
//        connection.setRequestMethod("POST");
//        connection.setDoInput(true);
//        connection.setDoOutput(true);
//
//        OutputStream os = connection.getOutputStream();
//        BufferedWriter writer = new BufferedWriter(
//                new OutputStreamWriter(os, "UTF-8"));
//        writer.write(getQuery(params));
//        writer.flush();
//        writer.close();
//        os.close();
//
//        connection.connect();
//    }
}
