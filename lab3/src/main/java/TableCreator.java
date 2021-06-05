import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.data.UdtValue;
import menager.SimpleManager;

public class TableCreator extends SimpleManager {

    public TableCreator(CqlSession session) {
        super(session);
    }
    public void createTable() {
        executeSimpleStatement(
                "CREATE TABLE IF NOT EXISTS car (\n" +
                        "    id int PRIMARY KEY,\n" +
                        "    mark text,\n" +
                        "    model text,\n" +
                        "    year int\n" +
                        ");");
        executeSimpleStatement(
                "CREATE TABLE IF NOT EXISTS transporter (\n" +
                        "    id int PRIMARY KEY,\n" +
                        "    firstname text,\n" +
                        "    lastname text,\n" +
                        "    age int\n" +
                        ");");
        executeSimpleStatement(
                "CREATE TABLE IF NOT EXISTS route (\n" +
                        "    id int PRIMARY KEY,\n" +
                        "    name text,\n" +
                        "    car int,\n" +
                        "    transporter int\n" +
                        ");");
    }
}
