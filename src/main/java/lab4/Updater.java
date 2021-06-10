package lab4;

import lab4.model.*;
import org.neo4j.ogm.session.Session;

public class Updater {
    private EmployeeService employeeService;
    Session session;

    public Updater(Session session) {
        this.session = session;
        this.employeeService = new EmployeeService(session);
    }
    public void init() {
        System.out.println("Zmienie imię pracownika o id 3 na Kasia");
        Employee employee = employeeService.read(3L);
        employee.setFirstname("Kasia");
        employeeService.createOrUpdate(employee);
    }
}
