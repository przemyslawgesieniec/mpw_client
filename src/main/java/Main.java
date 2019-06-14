import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Main {


    public static void main(String[] args) throws IOException, InterruptedException {

        List<DropBoxClient> listOfClients = new ArrayList<>();

        listOfClients.add(new DropBoxClient("Przemek"));
        listOfClients.add(new DropBoxClient("Ola"));
        listOfClients.add(new DropBoxClient("Adam"));
        listOfClients.add(new DropBoxClient("Michal"));
        listOfClients.add(new DropBoxClient("Krzysiek"));

        listOfClients.forEach(client -> new Thread(() -> {
            try {
                client.runClient();
            } catch (IOException | InterruptedException e) {
                System.out.println("ServerUnavailable");            }
        }).start());

    }
}
