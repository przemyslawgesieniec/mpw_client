import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Main {


    public static void main(String[] args) throws IOException, InterruptedException {

        List<DropBoxClient> listOfClients = new ArrayList<>();

        listOfClients.add(new DropBoxClient("przemek"));
//        listOfClients.add(new DropBoxClient("adam123"));
//        listOfClients.add(new DropBoxClient("adam"));

        listOfClients.forEach(client -> new Thread(() -> {
            try {
                client.runClient();
            } catch (IOException | InterruptedException e) {
                System.out.println("ServerUnavailable");            }
        }).start());

    }
}
