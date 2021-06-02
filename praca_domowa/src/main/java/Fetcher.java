import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;

import java.awt.*;
import java.util.*;
import java.util.List;

public class Fetcher {
    DynamoDBMapper mapper;

    public Fetcher(DynamoDBMapper mapper) {
        this.mapper = mapper;
    }

    public void getValues() {
        String operation;
        do{
            System.out.println("[1] Wyswietl wszystko\n[2] Wyszukaj po id\n[3] Wyszukaj po innych parametrach\n");
            Scanner scan = new Scanner(System.in);
            operation = scan.nextLine();
            switch (operation){
                case "1":
                    showAll();
                    break;
                case "2":
                    System.out.println("Wyszukuje Studenta z indeksem 2");
                    searchAfterId();
                    break;
                case "3":
                    showAfterOtherParameter();
                    break;
            }
        }while (!(operation.equals("1")||operation.equals("2")||operation.equals("3")));
    }

    private void showAfterOtherParameter() {
        System.out.println("Wyszukaj wszystkich pracowników ktorzy maja wiecej niz 30 lat");
        DynamoDBScanExpression dynamoDBScanExpression = new DynamoDBScanExpression();
        List<Employee> employeeList = mapper.scan(Employee.class, dynamoDBScanExpression);
        List<Employee> employeeListAfterCondition = new ArrayList<>();
        for(Employee e: employeeList){
            if(e.getAge()>=30) employeeListAfterCondition.add(e);
        }
        employeeListAfterCondition.forEach(System.out::println);
    }

    private void showAll() {
        DynamoDBScanExpression dynamoDBScanExpression = new DynamoDBScanExpression();
        List<Student> studentList = mapper.scan(Student.class, dynamoDBScanExpression);
        studentList.forEach(System.out::println);
        List<Employee> employeeList = mapper.scan(Employee.class, dynamoDBScanExpression);
        employeeList.forEach(System.out::println);
        List<Faculty> facultyList = mapper.scan(Faculty.class, dynamoDBScanExpression);
        facultyList.forEach(System.out::println);
    }
    private void searchAfterId(){
        int index = 2;
        Student student = mapper.load(Student.class, index);
        System.out.println(student);
    }
}
