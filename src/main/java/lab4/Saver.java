package lab4;

import lab4.model.*;
import org.neo4j.ogm.session.Session;

public class Saver {
    private BranchService branchService;
    private CarService carService;
    private EmployeeService employeeService;
    Session session;

    public Saver(Session session) {
        this.session = session;
        this.branchService = new BranchService(session);
        this.carService = new CarService(session);
        this.employeeService = new EmployeeService(session);
    }

    public void setInitialValues() {
        Car car1 = new Car("BMW","S3",2021);
        Car car2 = new Car("Audi","B1",2020);
        Car car3 = new Car("Volkswagen","A2",2017);
        Employee employee1 = new Employee("Jan","Kowalski",45);
        Employee employee2 = new Employee("Ola","Nowak",36);
        Employee employee3 = new Employee("Jerzy","Kot",53);
        Branch branch1 = new Branch("Kielce");
        Branch branch2 = new Branch("Krakow");
        Branch branch3 = new Branch("Warszawa");
        branch1.addCar(car1);
        branch1.addEmployee(employee1);
        branch2.addCar(car3);
        branch2.addEmployee(employee2);
        branch3.addCar(car2);
        branch3.addEmployee(employee3);
        carService.createOrUpdate(car1);
        carService.createOrUpdate(car2);
        carService.createOrUpdate(car3);
        employeeService.createOrUpdate(employee1);
        employeeService.createOrUpdate(employee2);
        employeeService.createOrUpdate(employee3);
        branchService.createOrUpdate(branch1);
        branchService.createOrUpdate(branch2);
        branchService.createOrUpdate(branch3);
    }
}
