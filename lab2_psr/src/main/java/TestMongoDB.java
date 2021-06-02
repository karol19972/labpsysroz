import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.elemMatch;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.exists;
import static com.mongodb.client.model.Filters.gt;
import static com.mongodb.client.model.Filters.lt;
import static com.mongodb.client.model.Filters.or;
import static com.mongodb.client.model.Projections.include;
import static com.mongodb.client.model.Updates.inc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

import org.bson.Document;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;

public class TestMongoDB {
    public static void main(String[] args) {

        String user = "student01";
        String password = "student01";
        String host = "localhost";
        int port = 27017;
        String database = "database01";

        String clientURI = "mongodb://" + user + ":" + password + "@" + host + ":" + port + "/" + database;
        MongoClientURI uri = new MongoClientURI(clientURI);

        MongoClient mongoClient = new MongoClient(uri);

        MongoDatabase db = mongoClient.getDatabase(database);

        db.getCollection("employees").drop();
        db.getCollection("students").drop();
        db.getCollection("faculty").drop();

        MongoCollection<Document> employees = db.getCollection("employees");
        MongoCollection<Document> students = db.getCollection("students");
        MongoCollection<Document> faculty = db.getCollection("faculty");

        System.out.println("[0] quit\n[1] save\n[2] update\n[3] delete\n[4] fetch\n[5] process\n");
        Scanner scan = new Scanner(System.in);
        String operation = scan.nextLine();
        do{
            switch (operation){
                case "0":
                    System.out.println("Program zakończono pomyślnie");
                    break;
                case "1":
                    Saver saver = new Saver(db);
                    saver.setInitialValues();
                    break;
                case "2":
                    Updater updater = new Updater(db);
                    updater.init();
                    break;
                case "3":
                    Cleaner cleaner = new Cleaner(db);
                    cleaner.init();
                    break;
                case "4":
                    Fetcher fetcher = new Fetcher(db);
                    fetcher.getValues();
                    break;
                case "5":
                    Processor processor = new Processor(db);
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

        mongoClient.close();
    }
}