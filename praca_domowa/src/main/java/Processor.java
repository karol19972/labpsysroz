import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;

import java.util.List;

public class Processor {
    DynamoDBMapper mapper;
    public Processor(DynamoDBMapper mapper) {
        this.mapper = mapper;
    }
    public void init() {
        DynamoDBScanExpression dynamoDBScanExpression = new DynamoDBScanExpression();
        List<Employee> employeeList = mapper.scan(Employee.class, dynamoDBScanExpression);
        double age = 0;
        for (Employee employee: employeeList){
            age += employee.getAge();
        }
        System.out.println("Średnia wieku pracownikow wynosi: "+age/employeeList.size());
    }
}
