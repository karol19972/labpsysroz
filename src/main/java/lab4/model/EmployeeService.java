package lab4.model;

import org.neo4j.ogm.session.Session;

public class EmployeeService extends GenericService<Employee> {

    public EmployeeService(Session session) {
		super(session);
	}
    
	@Override
	Class<Employee> getEntityType() {
		return Employee.class;
	}
    
}