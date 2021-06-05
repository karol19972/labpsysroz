import builder.CarsTableBuilderManager;
import com.datastax.oss.driver.api.core.CqlSession;

public class Cleaner {
    CqlSession session;

    public Cleaner(CqlSession session) {
        this.session = session;
    }

    public void init() {
        CarsTableBuilderManager tableManager = new CarsTableBuilderManager(session);
        tableManager.deleteFromTable();
    }
}
