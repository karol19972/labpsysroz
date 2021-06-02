import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

import java.util.Arrays;

public class Saver {
    private MongoDatabase db;

    public Saver(MongoDatabase db) {
        this.db = db;
    }

    public void setInitialValues() {
        MongoCollection<Document> employees = db.getCollection("employees");
        Document kowalska = new Document("_id", 1)
                .append("lastname", "Kowalska")
                .append("firstName", "Alicja")
                .append("sex","Kobieta")
                .append("age", 32);
        employees.insertOne(kowalska);
        Document jankowski = new Document("_id", 2)
                .append("lastname", "Jankowski")
                .append("firstName", "Robert")
                .append("sex","Mezczyzna")
                .append("age", 46);
        employees.insertOne(jankowski);
        Document witkowski = new Document("_id", 3)
                .append("lastname", "Witkowski")
                .append("firstName", "Stanoslaw")
                .append("sex","Mezczyzna")
                .append("age", 54);
        employees.insertOne(witkowski);
        Document markowski = new Document("_id", 4)
                .append("lastname", "Markowska")
                .append("firstName", "Ania")
                .append("sex","Kobieta")
                .append("age", 23);
        employees.insertOne(markowski);
        MongoCollection<Document> students = db.getCollection("students");
        Document kubiak = new Document("_id", 1)
                .append("lastname", "Kubiak")
                .append("firstName", "Antoni")
                .append("fieldOfStudy", "Informatyka")
                .append("grades", Arrays.asList(new Document("programming in C", 5.0), new Document("programming in Java", 4.0),
                        new Document("programming in C#", 3.0), new Document("programming network services", 4.5)));
        students.insertOne(kubiak);
        Document borkowski = new Document("_id", 2)
                .append("lastname", "Borkowski")
                .append("firstName", "Kamil")
                .append("fieldOfStudy", "Automatyka i robotyka")
                .append("grades", Arrays.asList(new Document("machines", 5.0), new Document("phisic", 4.0)));
        students.insertOne(borkowski);
        Document lis = new Document("_id", 3)
                .append("lastname", "Lis")
                .append("firstName", "Ola")
                .append("fieldOfStudy", "Budownictwo")
                .append("grades", Arrays.asList(new Document("materials", 5.0), new Document("phisic", 4.0),
                        new Document("minerals", 4.0)));
        students.insertOne(lis);
        Document kot = new Document("_id", 4)
                .append("lastname", "Kot")
                .append("firstName", "Paulina")
                .append("fieldOfStudy", "Budownictwo")
                .append("grades", Arrays.asList(new Document("materials", 3.0), new Document("phisic", 4.5),
                        new Document("minerals", 4.0)));
        students.insertOne(kot);
        MongoCollection<Document> faculties = db.getCollection("faculty");
        Document weaii = new Document("_id", 1)
                .append("name", "Wydzial eletrotechniki, automatyki i robotyki")
                .append("student", Arrays.asList(new Document("id1", 1), new Document("id2", 2)))
                .append("employee", Arrays.asList(new Document("id1", 1), new Document("id2", 2)));
        faculties.insertOne(weaii);
        Document wbia = new Document("_id", 2)
                .append("name", "Wydzial budownictwa i architektury")
                .append("student", Arrays.asList(new Document("id1", 3), new Document("id2", 4)))
                .append("employee", Arrays.asList(new Document("id1", 3)));
        faculties.insertOne(wbia);
    }
}
