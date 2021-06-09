import Models.Branch;
import Models.Car;
import Models.Employee;
import com.orientechnologies.orient.core.metadata.schema.OClass;
import com.orientechnologies.orient.core.sql.executor.OResult;
import com.orientechnologies.orient.core.sql.executor.OResultSet;
import java.util.*;

public class Fetcher {
    public  void getValues() {
        String operation;
        do{
            System.out.println("[1] Wyswietl wszystko\n[2] Wyszukaj po id\n[3] Wyszukaj po innych parametrach\n");
            Scanner scan = new Scanner(System.in);
            operation = scan.nextLine();
            switch (operation){
                case "1":
                    getAllCars();
                    getAllBranches();
                    getAllEmployees();
                    break;
                case "2":
                    System.out.println("Wyszukam samochod z id 2");
                    searchAfterId();
                    break;
                case "3":
                    System.out.println("Wyszukam pracownikow starszych od 40-latkow");
                    searchAfterOtherParameters();
                    break;
            }
        }while (!(operation.equals("1")||operation.equals("2")||operation.equals("3")));
    }

    private void searchAfterOtherParameters() {
        String statement = "SELECT * FROM Employee where age>40";
        OResultSet rs = Main.db.query(statement);
        List<Employee> employees = new ArrayList<>();
        while(rs.hasNext()){
            OResult row = rs.next();
            Employee employee = new Employee();
            employee.setId(row.getProperty("id"));
            employee.setFirstname(row.getProperty("firstname"));
            employee.setLastname(row.getProperty("lastname"));
            employee.setAge(row.getProperty("age"));
            employees.add(employee);
        }
        System.out.println(employees);
        rs.close();
    }

    public void searchAfterId(){
        String statement = "SELECT * FROM Car WHERE id=2";
        OResultSet rs = Main.db.query(statement);
        List<Car> carList = new ArrayList<>();
        while(rs.hasNext()){
            OResult row = rs.next();
            Car car = new Car();
            car.setId(row.getProperty("id"));
            car.setMark(row.getProperty("mark"));
            car.setModel(row.getProperty("model"));
            car.setYear(row.getProperty("year"));
            carList.add(car);
        }
        System.out.println(carList);
        rs.close();
    }

    public void getAllCars(){
        String statement = "SELECT * FROM Car";
        OResultSet rs = Main.db.query(statement);
        List<Car> carList = new ArrayList<>();
        while(rs.hasNext()){
            OResult row = rs.next();
            Car car = new Car();
            car.setId(row.getProperty("id"));
            car.setMark(row.getProperty("mark"));
            car.setModel(row.getProperty("model"));
            car.setYear(row.getProperty("year"));
            carList.add(car);
        }
        System.out.println(carList);
        rs.close();
    }
    public void getAllBranches(){
        String statement = "SELECT * FROM Branch";
        OResultSet rs = Main.db.query(statement);
        List<Branch> branches = new ArrayList<>();
        while(rs.hasNext()){
            OResult row = rs.next();
            Branch branch = new Branch();
            branch.setId(row.getProperty("id"));
            branch.setCar_id(row.getProperty("car"));
            branch.setEmployee_id(row.getProperty("employee"));
            branch.setCity(row.getProperty("city"));
            branches.add(branch);
        }
        System.out.println(branches);
        rs.close();
    }

    public void getAllEmployees(){
        String statement = "SELECT * FROM Employee";
        OResultSet rs = Main.db.query(statement);
        List<Employee> employees = new ArrayList<>();
        while(rs.hasNext()){
            OResult row = rs.next();
            Employee employee = new Employee();
            employee.setId(row.getProperty("id"));
            employee.setFirstname(row.getProperty("firstname"));
            employee.setLastname(row.getProperty("lastname"));
            employee.setAge(row.getProperty("age"));
            employees.add(employee);
        }
        System.out.println(employees);
        rs.close();
    }
}
