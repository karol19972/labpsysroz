import Model.Car;
import Model.Route;
import Model.Transporter;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.util.CosmosPagedIterable;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Fetcher {
    CosmosContainer containerCars,containerRoute,containerTransporter;

    public Fetcher(CosmosContainer containerCars, CosmosContainer containerRoute, CosmosContainer containerTransporter) {
        this.containerCars = containerCars;
        this.containerRoute = containerRoute;
        this.containerTransporter = containerTransporter;
    }


    public void getValues() {
        String operation;
        do{
            System.out.println("[1] Wyswietl wszystko\n[2] Wyszukaj po id\n[3] Wyszukaj po innych parametrach\n");
            Scanner scan = new Scanner(System.in);
            operation = scan.nextLine();
            switch (operation){
                case "1":
                    search("car");
                    search("route");
                    search("transporter");
                    break;
                case "2":
                    System.out.println("Wyszukam samochod z id 2");
                    searchAfterId();
                    break;
                case "3":
                    System.out.println("Wyszukam transporterow starszych od 30-latkow");
                    searchAfterOtherParameters();
                    break;
            }
        }while (!(operation.equals("1")||operation.equals("2")||operation.equals("3")));

    }
    private void search(String name) {
        CosmosQueryRequestOptions queryOptions = new CosmosQueryRequestOptions();
        queryOptions.setQueryMetricsEnabled(true);
        CosmosPagedIterable<?> carsPagedIterable = null;
        if(name.equals("car")) carsPagedIterable = containerCars.queryItems(
                "SELECT * FROM Car", queryOptions, Car.class);
        if(name.equals("route")) carsPagedIterable = containerRoute.queryItems(
                "SELECT * FROM Route", queryOptions, Route.class);
        if(name.equals("transporter")) carsPagedIterable = containerTransporter.queryItems(
                "SELECT * FROM Transporter", queryOptions, Transporter.class);
        carsPagedIterable.iterableByPage().forEach(cosmosItemPropertiesFeedResponse -> {
            for(Object o: cosmosItemPropertiesFeedResponse.getResults()){
                System.out.println(o);
            }
        });
    }
    private void searchAfterId() {
        CosmosQueryRequestOptions queryOptions = new CosmosQueryRequestOptions();
        queryOptions.setQueryMetricsEnabled(true);

        CosmosPagedIterable<Car> carsPagedIterable = containerCars.queryItems(
                "SELECT * FROM Car WHERE Car.id = '2'", queryOptions, Car.class);

        carsPagedIterable.iterableByPage().forEach(cosmosItemPropertiesFeedResponse -> {
            for(Car car: cosmosItemPropertiesFeedResponse.getResults()){
                System.out.println(car);
            }
        });
    }

    private void searchAfterOtherParameters() {
        CosmosQueryRequestOptions queryOptions = new CosmosQueryRequestOptions();
        queryOptions.setQueryMetricsEnabled(true);

        CosmosPagedIterable<Transporter> carsPagedIterable = containerTransporter.queryItems(
                "SELECT * FROM Transporter WHERE Transporter.age > 30", queryOptions, Transporter.class);

        carsPagedIterable.iterableByPage().forEach(cosmosItemPropertiesFeedResponse -> {
            for(Transporter transporter: cosmosItemPropertiesFeedResponse.getResults()){
                System.out.println(transporter);
            }
        });
    }
}
