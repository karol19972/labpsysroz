import builder.CarsTableBuilderManager;
import com.datastax.oss.driver.api.core.CqlSession;

public class Updater {
    private CqlSession session;
    public Updater(CqlSession session) {
        this.session = session;
    }

    public void init() {
        System.out.println("zmienie imie pierwszego przewoznika(transporter) na Kasia");
        CarsTableBuilderManager tableManager = new CarsTableBuilderManager(session);
        tableManager.updateIntoTable();
    }
}
