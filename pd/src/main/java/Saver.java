import Model.Car;
import Model.Route;
import Model.Transporter;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.PartitionKey;


import java.util.ArrayList;
import java.util.List;

public class Saver {
    ArrayList<Car> cars;
    ArrayList<Transporter> transporters;
    ArrayList<Route> routes;
    CosmosContainer containerCars,containerRoute,containerTransporter;

    public Saver(CosmosContainer containerCars, CosmosContainer containerRoute, CosmosContainer containerTransporter) {
        this.containerCars = containerCars;
        this.containerRoute = containerRoute;
        this.containerTransporter = containerTransporter;
    }

    public void setInitialValues() throws Exception {
          cars = new ArrayList<>();
          transporters = new ArrayList<>();
          routes = new ArrayList<>();
          fillListOfCars();
          createCars();
          fillListOfTransporters();
          createTransporters();
          fillListOfRoutes();
          createRoutes();
    }

    private void createCars() throws Exception {
        double totalRequestCharge = 0;
        for (Car car : cars) {
            CosmosItemResponse item = containerCars.createItem(car, new PartitionKey(car.getPartitionKey()), new CosmosItemRequestOptions());
            totalRequestCharge += item.getRequestCharge();
        }
    }

    private void createTransporters() throws Exception {
        double totalRequestCharge = 0;
        for (Transporter transporter : transporters) {
            CosmosItemResponse item = containerTransporter.createItem(transporter, new PartitionKey(transporter.getPartitionKey()), new CosmosItemRequestOptions());
            totalRequestCharge += item.getRequestCharge();
        }
    }
    private void createRoutes() throws Exception {
        double totalRequestCharge = 0;
        for (Route route : routes) {
            CosmosItemResponse item = containerRoute.createItem(route, new PartitionKey(route.getPartitionKey()), new CosmosItemRequestOptions());
            totalRequestCharge += item.getRequestCharge();
        }
    }
    private void fillListOfCars(){
        Car car1 = new Car("Car1","1","BMW","S3",2021);
        Car car2 = new Car("Car2","2","Audi","A3",2020);
        cars.add(car1);
        cars.add(car2);
    }
    private void fillListOfTransporters(){
        Transporter transporter1 = new Transporter("Transporter1","1","Anna","Kowalska",34);
        Transporter transporter2 = new Transporter("Transporter2","2","Marcin","Kot",27);
        Transporter transporter3 = new Transporter("Transporter3","3","Jan","Nowak",56);
        transporters.add(transporter1);
        transporters.add(transporter2);
        transporters.add(transporter3);
    }

    private void fillListOfRoutes(){
       Route route1 = new Route("Route1","1","Wroclaw",0,0);
       Route route2 = new Route("Route2","2","Olsztyn",1,1);
       Route route3 = new Route("Route3","3","Warszawa",1,2);
       routes.add(route1);
       routes.add(route2);
       routes.add(route3);
    }
}
