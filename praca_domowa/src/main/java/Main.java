import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.model.*;
import java.util.*;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;

public class Main {
    private static final String tableStudentsName = "Student";
    private static final String tableFacultiesName = "Faculties";
    private static final String tableEmployeesName = "Employees";

    public static void main(String [] args){
        AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard()
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration("http://localhost:8000", "ue-west-1"))
                .build();

        DynamoDB dynamoDB = new DynamoDB(client);
        DynamoDBMapper mapper = new DynamoDBMapper(client);


        try {
            dynamoDB.getTable(tableStudentsName).delete();
            dynamoDB.getTable(tableFacultiesName).delete();
            dynamoDB.getTable(tableEmployeesName).delete();

            System.out.println("Attempting to create table; please wait...");
            CreateTableRequest createTableRequest = mapper.generateCreateTableRequest(Student.class);
            createTableRequest.setProvisionedThroughput(new ProvisionedThroughput(25L, 25L));
            dynamoDB.createTable(createTableRequest);
            createTableRequest = mapper.generateCreateTableRequest(Faculty.class);
            createTableRequest.setProvisionedThroughput(new ProvisionedThroughput(25L, 25L));
            dynamoDB.createTable(createTableRequest);
            createTableRequest = mapper.generateCreateTableRequest(Employee.class);
            createTableRequest.setProvisionedThroughput(new ProvisionedThroughput(25L, 25L));
            dynamoDB.createTable(createTableRequest);

            System.out.println("[0] quit\n[1] save\n[2] update\n[3] delete\n[4] fetch\n[5] process\n");
            Scanner scan = new Scanner(System.in);
            String operation = scan.nextLine();
            do{
                switch (operation){
                    case "0":
                        System.out.println("Program zakończono pomyślnie");
                        break;
                    case "1":
                        Saver saver = new Saver(mapper);
                        saver.setInitialValues();
                        break;
                    case "2":
                        Updater updater = new Updater(mapper);
                        updater.init();
                        break;
                    case "3":
                        Cleaner cleaner = new Cleaner(dynamoDB,mapper);
                        cleaner.init();
                        break;
                    case "4":
                        Fetcher fetcher = new Fetcher(mapper);
                        fetcher.getValues();
                        break;
                    case "5":
                        Processor processor = new Processor(mapper);
                        processor.init();
                        break;
                    default:
                        System.out.println("Podałeś nieprawidlowa operacje! Wybierz jadna z podanych");
                        break;
                }
                if(!operation.equals("0")) {
                    System.out.println("[0] quit\n[1] save\n[2] update\n[3] delete\n[4] fetch\n[5] process\n");
                    operation = scan.nextLine();
                }
            }while (!operation.equals("0"));

        }
        catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }



}
