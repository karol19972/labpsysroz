import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

import static com.mongodb.client.model.Filters.eq;

public class Updater {
    private MongoDatabase db;
    private MongoCollection<Document> faculties,employees,students;

    public Updater(MongoDatabase db) {
        this.db = db;
        employees = db.getCollection("employees");

    }
    public void init() {
        System.out.println("Zmiana polega na zamianie imienia na pierwszego pracownika na Justyna");
        employees.updateOne(eq("_id", 1), new Document("$set", new Document("firstName", "Justyna")));
    }
}
