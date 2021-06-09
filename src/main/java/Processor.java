import Models.Employee;
import com.orientechnologies.orient.core.sql.executor.OResult;
import com.orientechnologies.orient.core.sql.executor.OResultSet;

import java.util.ArrayList;
import java.util.List;

public class Processor {
    List<Employee> employees;
    public void init() {
        System.out.println("Policzę średnią wieku pracowników");
        employees = new ArrayList<>();
        getAllEmployees();
        double sum = 0;
        for(Employee employee: employees){
            sum+=employee.getAge();
        }
        System.out.println("Srednia wieku pracownikow wynosi: "+sum/employees.size());
    }

    public void getAllEmployees(){
        String statement = "SELECT * FROM Employee";
        OResultSet rs = Main.db.query(statement);

        while(rs.hasNext()){
            OResult row = rs.next();
            Employee employee = new Employee();
            employee.setId(row.getProperty("id"));
            employee.setFirstname(row.getProperty("firstname"));
            employee.setLastname(row.getProperty("lastname"));
            employee.setAge(row.getProperty("age"));
            employees.add(employee);
        }
        rs.close();
    }
}
