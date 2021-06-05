import builder.CarsTableBuilderManager;
import com.datastax.oss.driver.api.core.CqlSession;

public class Fetcher {
    private CqlSession session;
    public Fetcher(CqlSession session) {
        this.session = session;
    }

    public void getValues() {
        CarsTableBuilderManager tableManager = new CarsTableBuilderManager(session);
        tableManager.selectFromTable();
    }
}
