import Models.Branch;
import Models.Car;
import Models.Employee;
import com.orientechnologies.orient.core.record.ORecord;
import com.orientechnologies.orient.core.sql.executor.OResult;
import com.orientechnologies.orient.core.sql.executor.OResultSet;

import java.util.ArrayList;
import java.util.List;

public class Cleaner {
    List<ORecord> employees;
    List<ORecord> branches;
    List<ORecord> cars;

    public Cleaner() {
        this.employees = new ArrayList<>();
        this.branches = new ArrayList<>();
        this.cars = new ArrayList<>();
    }

    public void init() {
        getAllBranches();
        getAllCars();
        getAllEmployees();
        delete(employees);
        delete(cars);
        delete(branches);


    }

    private void delete(List<ORecord> list) {
        for (int i = 0; i < list.size(); i++) {
            Main.db.delete(list.get(i));
        }
    }


    public void getAllCars() {
        String statement = "SELECT * FROM Car";
        OResultSet rs = Main.db.query(statement);
        while (rs.hasNext()) {
            OResult row = rs.next();

            if (row.getRecord().isPresent())
                cars.add(row.getRecord().get());
        }
        rs.close();
    }

    public void getAllBranches() {
        String statement = "SELECT * FROM Branch";
        OResultSet rs = Main.db.query(statement);

        while (rs.hasNext()) {
            OResult row = rs.next();
            if (row.getRecord().isPresent())
                branches.add(row.getRecord().get());
        }
        rs.close();
    }

    public void getAllEmployees() {
        String statement = "SELECT * FROM Employee";
        OResultSet rs = Main.db.query(statement);

        while (rs.hasNext()) {
            OResult row = rs.next();
            if (row.getRecord().isPresent())
                employees.add(row.getRecord().get());
        }
        rs.close();
    }
}
