import builder.CarsTableBuilderManager;
import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.CqlSession;


public class Saver {
    private CqlSession session;
    public Saver(CqlSession session) {
        this.session = session;
    }

    public void setInitialValues() {
        CarsTableBuilderManager tableManager = new CarsTableBuilderManager(session);
        tableManager.insertIntoTable();
    }


}
