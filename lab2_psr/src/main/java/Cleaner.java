import com.mongodb.BasicDBObject;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.result.DeleteResult;
import org.bson.Document;

public class Cleaner {
    private MongoDatabase db;
    private MongoCollection<Document> faculties,employees,students;

    public Cleaner(MongoDatabase db) {
        this.db = db;
        faculties = db.getCollection("faculty");
        employees = db.getCollection("employees");
        students = db.getCollection("students");
    }
    public void init() {
        delete(faculties);
        delete(employees);
        delete(students);
    }
    private void delete(MongoCollection<Document> mongoCollection){
        FindIterable<Document> findIterable = mongoCollection.find();
        for (Document document : findIterable) {
            mongoCollection.deleteMany(document);
        }
    }
}
