import Model.Car;
import Model.Route;
import Model.Transporter;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.util.CosmosPagedIterable;

import java.util.ArrayList;
import java.util.List;

public class Cleaner {

    CosmosContainer containerCars, containerRoute, containerTransporter;
    List<Car> cars;
    List<Transporter> transporters;
    List<Route> routes;

    public Cleaner(CosmosContainer containerCars, CosmosContainer containerRoute, CosmosContainer containerTransporter) {
        this.containerCars = containerCars;
        this.containerRoute = containerRoute;
        this.containerTransporter = containerTransporter;
        this.cars = new ArrayList<>();
        this.transporters = new ArrayList<>();
        this.routes = new ArrayList<>();
    }

    public void init() {
        fetch("car");
        fetch("route");
        fetch("transporter");
        for (Car car : cars)
            this.containerCars.deleteItem(car, new CosmosItemRequestOptions());
        for (Transporter transporter : transporters)
            this.containerTransporter.deleteItem(transporter, new CosmosItemRequestOptions());
        for (Route route : routes)
            this.containerRoute.deleteItem(route, new CosmosItemRequestOptions());

    }

    private void fetch(String name) {
        CosmosQueryRequestOptions queryOptions = new CosmosQueryRequestOptions();
        queryOptions.setQueryMetricsEnabled(true);
        CosmosPagedIterable<?> carsPagedIterable = null;
        if (name.equals("car")) carsPagedIterable = containerCars.queryItems(
                "SELECT * FROM Car", queryOptions, Car.class);
        if (name.equals("route")) carsPagedIterable = containerRoute.queryItems(
                "SELECT * FROM Route", queryOptions, Route.class);
        if (name.equals("transporter")) carsPagedIterable = containerTransporter.queryItems(
                "SELECT * FROM Transporter", queryOptions, Transporter.class);
        carsPagedIterable.iterableByPage().forEach(cosmosItemPropertiesFeedResponse -> {
            for (Object o : cosmosItemPropertiesFeedResponse.getResults()) {
                if (o instanceof Car) cars.add((Car) o);
                if (o instanceof Route) routes.add((Route) o);
                if (o instanceof Transporter) transporters.add((Transporter) o);
            }
        });
    }
}
