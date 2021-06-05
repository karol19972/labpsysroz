
import com.azure.cosmos.*;
import com.azure.cosmos.models.*;
import java.util.ArrayList;
import java.util.Scanner;

public class Main {
    private CosmosClient client;

    private final String databaseName = "ToDoList";
    private final String containerNameCars = "CarContainer";
    private final String containerNameRoute = "RouteContainer";
    private final String containerNameTransporter = "TransporterContainer";

    private CosmosDatabase database;
    private CosmosContainer containerCars;
    private CosmosContainer containerRoute;
    private CosmosContainer containerTransporter;

    public void close() {
        client.close();
    }

    public static void main(String[] args) {
        Main p = new Main();

        try {
            p.getStartedDemo();
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println(String.format("Cosmos getStarted failed with %s", e));
        } finally {
            System.out.println("Closing the client");
            p.close();
        }
        System.exit(0);
    }

    //  </Main>

    private void getStartedDemo() throws Exception {
        ArrayList<String> preferredRegions = new ArrayList<String>();
        preferredRegions.add("West US");

        client = new CosmosClientBuilder()
                .endpoint(AccountSettings.HOST)
                .key(AccountSettings.MASTER_KEY)
                .preferredRegions(preferredRegions)
                .userAgentSuffix("CosmosDBJavaQuickstart")
                .consistencyLevel(ConsistencyLevel.EVENTUAL)
                .buildClient();

        createDatabaseIfNotExists();
        createContainerIfNotExists();
        System.out.println("[0] quit\n[1] save\n[2] update\n[3] delete\n[4] fetch\n[5] process\n");
        Scanner scan = new Scanner(System.in);
        String operation = scan.nextLine();
        do{
            switch (operation){
                case "0":
                    System.out.println("Program zakończono pomyślnie");
                    break;
                case "1":
                    Saver saver = new Saver(containerCars,containerRoute,containerTransporter);
                    saver.setInitialValues();
                    break;
                case "2":
                    Updater updater = new Updater(containerTransporter);
                    updater.init();
                    break;
                case "3":
                    Cleaner cleaner = new Cleaner(containerCars,containerRoute,containerTransporter);
                    cleaner.init();
                    break;
                case "4":
                    Fetcher fetcher = new Fetcher(containerCars,containerRoute,containerTransporter);
                    fetcher.getValues();
                    break;
                case "5":
                    Processor processor = new Processor(containerTransporter);
                    processor.init();
                    break;
                default:
                    System.out.println("Podałeś nieprawidlowa operacje! Wybierz jadna z podanych");
                    break;
            }
            if(!operation.equals("0")) {
                System.out.println("[0] quit\n[1] save\n[2] update\n[3] delete\n[4] fetch\n[5] process\n");
                operation = scan.nextLine();
            }
        }while (!operation.equals("0"));
    }

    private void createDatabaseIfNotExists() throws Exception {
        CosmosDatabaseResponse databaseResponse = client.createDatabaseIfNotExists(databaseName);
        database = client.getDatabase(databaseResponse.getProperties().getId());
    }

    private void createContainerIfNotExists() throws Exception {
        CosmosContainerProperties containerProperties =
                new CosmosContainerProperties(containerNameCars, "/partitionKey");
        CosmosContainerResponse containerResponse = database.createContainerIfNotExists(containerProperties);
        containerCars = database.getContainer(containerResponse.getProperties().getId());
        containerProperties =
                new CosmosContainerProperties(containerNameRoute, "/partitionKey");
        containerResponse = database.createContainerIfNotExists(containerProperties);
        containerRoute = database.getContainer(containerResponse.getProperties().getId());
        containerProperties =
                new CosmosContainerProperties(containerNameTransporter, "/partitionKey");
        containerResponse = database.createContainerIfNotExists(containerProperties);
        containerTransporter = database.getContainer(containerResponse.getProperties().getId());
    }


}
