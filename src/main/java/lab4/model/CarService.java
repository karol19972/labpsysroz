package lab4.model;

import org.neo4j.ogm.session.Session;

public class CarService extends GenericService<Car> {

    public CarService(Session session) {
		super(session);
	}
    
	@Override
	Class<Car> getEntityType() {
		return Car.class;
	}
    
}