import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

public class Main {


    public static void main(String[] args) throws IOException, InterruptedException {

        List<MpwClient> listOfClients = new ArrayList<>();

        listOfClients.add(new MpwClient("Przemek"));
        listOfClients.add(new MpwClient("Ola"));
        listOfClients.add(new MpwClient("Adam"));
        listOfClients.add(new MpwClient("Michal"));
        listOfClients.add(new MpwClient("Krzysiek"));

        listOfClients.forEach(client -> new Thread(() -> {
            try {
                client.start();
            } catch (IOException | InterruptedException | URISyntaxException e) {
                System.out.println("ServerUnavailable");
            }
        }).start());

    }
}
