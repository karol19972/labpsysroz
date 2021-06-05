import Model.Transporter;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.util.CosmosPagedIterable;

import java.util.concurrent.atomic.AtomicReference;

public class Updater {
    CosmosContainer containerTransporter;

    public Updater(CosmosContainer containerTransporter) {
        this.containerTransporter = containerTransporter;
    }

    public void init() {
        System.out.println("Zmiana będzie dotyczyła zmiany pierwszego imienia transportera na Kasia");
        Transporter transporter = searchAfterId();
        if(transporter==null) System.out.println("Nie ma transportera o id 1");
        else {
            transporter.setFirstname("Kasia");
            containerTransporter.upsertItem(transporter);
        }
    }
    private Transporter searchAfterId() {
        CosmosQueryRequestOptions queryOptions = new CosmosQueryRequestOptions();
        queryOptions.setQueryMetricsEnabled(true);

        CosmosPagedIterable<Transporter> carsPagedIterable = containerTransporter.queryItems(
                "SELECT * FROM Transporter WHERE Transporter.id = '1'", queryOptions, Transporter.class);
        AtomicReference<Transporter> transporter = new AtomicReference<>();
        carsPagedIterable.iterableByPage().forEach(cosmosItemPropertiesFeedResponse -> {
            for(Transporter t: cosmosItemPropertiesFeedResponse.getResults()){
                transporter.set(t);
            }
        });
        return transporter.get();

    }
}
