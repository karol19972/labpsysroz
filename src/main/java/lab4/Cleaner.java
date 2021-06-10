package lab4;

import lab4.model.BranchService;
import lab4.model.CarService;
import lab4.model.EmployeeService;
import org.neo4j.ogm.session.Session;

public class Cleaner {
    private BranchService branchService;
    private CarService carService;
    private EmployeeService employeeService;
    Session session;

    public Cleaner(Session session) {
        this.session = session;
        this.branchService = new BranchService(session);
        this.carService = new CarService(session);
        this.employeeService = new EmployeeService(session);
    }

    public void init() {
        branchService.deleteAll();
        carService.deleteAll();
        employeeService.deleteAll();
    }
}
