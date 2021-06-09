import Models.Branch;
import Models.Car;
import Models.Employee;
import com.orientechnologies.orient.core.record.OVertex;

public class Saver {
    public void setInitialValues() {
          createCar(new Car(1,"BMW","S3",2021));
          createCar(new Car(2,"Audi","B1",2020));
          createCar(new Car(3,"Volkswagen","A2",2017));
          createEmployee(new Employee(1,"Jan","Kowalski",45));
          createEmployee(new Employee(2,"Ola","Nowak",36));
          createEmployee(new Employee(3,"Jerzy","Kot",53));
          createBranch(new Branch(1,1,3,"Kielce"));
          createBranch(new Branch(2,2,3,"Krakow"));
          createBranch(new Branch(3,3,2,"Warszawa"));
    }

    private static void createCar(Car car){
        OVertex result = Main.db.newVertex("Car");
        result.setProperty("id", car.getId());
        result.setProperty("mark", car.getMark());
        result.setProperty("model", car.getModel());
        result.setProperty("year", car.getYear());
        result.save();
    }
    private static void createEmployee(Employee employee){
        OVertex result = Main.db.newVertex("Employee");
        result.setProperty("id", employee.getId());
        result.setProperty("firstname", employee.getFirstname());
        result.setProperty("lastname", employee.getLastname());
        result.setProperty("age", employee.getAge());
        result.save();
    }
    private static void createBranch(Branch branch){
        OVertex result = Main.db.newVertex("Branch");
        result.setProperty("id", branch.getId());
        result.setProperty("car", branch.getCar_id());
        result.setProperty("employee", branch.getEmployee_id());
        result.setProperty("city", branch.getCity());
        result.save();
    }


}
