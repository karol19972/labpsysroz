package lab4;

import lab4.model.BranchService;
import lab4.model.CarService;
import lab4.model.Employee;
import lab4.model.EmployeeService;
import org.neo4j.ogm.session.Session;

import java.util.*;

public class Processor {
    private BranchService branchService;
    private CarService carService;
    private EmployeeService employeeService;
    Session session;
    List<Employee> employees;

    public Processor(Session session) {
        this.session = session;
        this.branchService = new BranchService(session);
        this.carService = new CarService(session);
        this.employeeService = new EmployeeService(session);
        employees = new ArrayList<>();
    }


    public void init() {
        System.out.println("Policzę średnią wieku pracowników");
        getAllEmployees();
        double sum = 0;
        for(Employee employee: employees){
            sum+=employee.getAge();
        }
        System.out.println("Srednia wieku pracownikow wynosi: "+sum/employees.size());
    }

    private void getAllEmployees() {
        for(Employee employee : employeeService.readAll())
            employees.add(employee);
    }

}
