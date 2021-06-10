package lab4.model;


import org.neo4j.ogm.session.Session;

public class BranchService extends GenericService<Branch> {

    public BranchService(Session session) {
		super(session);
	}
    
	@Override
	Class<Branch> getEntityType() {
		return Branch.class;
	}
    
}