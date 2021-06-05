import builder.CarsTableBuilderManager;
import com.datastax.oss.driver.api.core.CqlSession;

public class Processor {
    CqlSession session;

    public Processor(CqlSession session) {
        this.session = session;
    }

    public void init() {
        System.out.println("Średnia wieku przewoznikow(transporterow) wynosi: ");
        CarsTableBuilderManager tableManager = new CarsTableBuilderManager(session);
        tableManager.process();
    }
}
