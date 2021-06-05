import Model.Transporter;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.util.CosmosPagedIterable;

import java.util.ArrayList;
import java.util.List;

public class Processor {
    CosmosContainer containerTransporter;
    List<Transporter> transporters;

    public Processor(CosmosContainer containerTransporter) {
        this.containerTransporter = containerTransporter;
        this.transporters = new ArrayList<>();
    }

    public void init() {
        searchAllTransporters();
        int sum = 0;
        int iter = 0;
        for (Transporter t : transporters) {
            sum += t.getAge();
            iter++;
        }
        System.out.print("Średni wiek transporterow wynosi: "+(double) sum / iter+"\n");
    }

    private void searchAllTransporters() {
        CosmosQueryRequestOptions queryOptions = new CosmosQueryRequestOptions();
        queryOptions.setQueryMetricsEnabled(true);

        CosmosPagedIterable<Transporter> carsPagedIterable = containerTransporter.queryItems(
                "SELECT * FROM Transporter", queryOptions, Transporter.class);

        carsPagedIterable.iterableByPage().forEach(cosmosItemPropertiesFeedResponse -> {
            for (Transporter transporter : cosmosItemPropertiesFeedResponse.getResults()) {
                transporters.add(transporter);
            }
        });
    }
}
