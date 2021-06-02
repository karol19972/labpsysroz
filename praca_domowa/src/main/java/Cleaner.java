import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.dynamodbv2.document.DeleteItemOutcome;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.spec.DeleteItemSpec;
import com.amazonaws.services.dynamodbv2.document.utils.NameMap;
import com.amazonaws.services.dynamodbv2.document.utils.ValueMap;
import com.amazonaws.services.dynamodbv2.model.ReturnValue;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;

import java.util.List;

public class Cleaner {
    DynamoDB dynamoDB;
    DynamoDBMapper mapper;
    private static final String tableStudentsName = "Student";
    private static final String tableFacultiesName = "Faculties";
    private static final String tableEmployeesName = "Employees";
    public Cleaner(DynamoDB dynamoDB,DynamoDBMapper mapper) {
        this.dynamoDB = dynamoDB;
        this.mapper = mapper;
    }

    public void init() {
        DynamoDBScanExpression dynamoDBScanExpression = new DynamoDBScanExpression();
        List<Student> studentList = mapper.scan(Student.class, dynamoDBScanExpression);
        List<Employee> employeeList = mapper.scan(Employee.class, dynamoDBScanExpression);
        List<Faculty> facultyList = mapper.scan(Faculty.class, dynamoDBScanExpression);
        for(Student s: studentList) mapper.delete(s);
        for(Employee e: employeeList) mapper.delete(e);
        for(Faculty f: facultyList) mapper.delete(f);
    }
}
