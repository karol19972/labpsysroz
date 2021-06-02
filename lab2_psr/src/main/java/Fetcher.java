import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import java.util.Scanner;
import org.bson.Document;

import static com.mongodb.client.model.Filters.*;

public class Fetcher {
    private MongoDatabase db;
    private MongoCollection<Document> faculties,employees,students;

    public Fetcher(MongoDatabase db) {
        this.db = db;
        faculties = db.getCollection("faculty");
        employees = db.getCollection("employees");
        students = db.getCollection("students");
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
                    showAfterId();
                    break;
                case "3":
                    showAfterOtherParameter();
                    break;
            }
        }while (!(operation.equals("1")||operation.equals("2")||operation.equals("3")));
    }

    private void showAfterOtherParameter() {
        System.out.println("Wyszukam wszystkich pracowników gdzie wiek jest większy niż 40 lat");
        BasicDBObject query = new BasicDBObject();
        query.put("age", new BasicDBObject("$gt", 40));

        for (Document doc : employees.find(query)) {
            System.out.println(doc.toJson());
        }
    }

    private void showAfterId() {
        String operation;
        do{
            System.out.println("[1] Wyswietl pracowników\n[2] Wyswietl studentów\n[3] Wyswietl wydziały\n");
            Scanner scan = new Scanner(System.in);
            operation = scan.nextLine();
            String id;
            switch (operation){
                case "1":
                    findRecordAfterId(employees);
                    break;
                case "2":
                    findRecordAfterId(students);
                    break;
                case "3":
                    findRecordAfterId(faculties);
                    break;
            }
        }while (!(operation.equals("1")||operation.equals("2")||operation.equals("3")));
    }

    private boolean findRecordAfterId(MongoCollection<Document> mongoCollection){
        String id = typeId();
        int idInt;
        try {
            idInt = Integer.parseInt(id);
        }catch (Exception exception){
            System.out.println("Nie podales liczby");
            return false;
        }
        try {
            Document myDoc = mongoCollection.find(eq("_id", idInt)).first();
            System.out.println(myDoc.toJson());
        }catch (Exception e){
            System.out.println("Nie znaleziono id");
            return false;
        }
        return true;
    }
    private String typeId(){
        System.out.println("Wpisz id");
        Scanner scan = new Scanner(System.in);
        String id = scan.nextLine();
        return id;
    }

    private void showAll() {

        for (Document doc : faculties.find())
            System.out.println("Wydziały: " + doc.toJson());
        for (Document doc : employees.find())
            System.out.println("Pracownicy: " + doc.toJson());
        for (Document doc : students.find())
            System.out.println("Studenci: " + doc.toJson());
    }
}
