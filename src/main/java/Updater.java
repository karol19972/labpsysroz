import Models.Employee;
import com.orientechnologies.orient.core.sql.executor.OResultSet;

import java.util.ArrayList;
import java.util.List;

public class Updater {
    List<Employee> employeeList;

    public void init() {
        this.employeeList = new ArrayList<>();
        System.out.println("Zmieniam imię pierwszego pracownika na Kasia");
        update();


    }
    public void update(){
        String statement = "SELECT * FROM Employee WHERE id=1";
        OResultSet rs = Main.db.query(statement);
        while(rs.hasNext()){

            rs.next().getVertex().ifPresent(x->{
                x.setProperty("firstname", "Kasia");
                x.save();
            });
        }
        rs.close();
    }
}
