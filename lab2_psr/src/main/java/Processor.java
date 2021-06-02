import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Arrays;

import static com.mongodb.client.model.Accumulators.avg;
import static com.mongodb.client.model.Aggregates.*;
import static com.mongodb.client.model.Sorts.ascending;

public class Processor {
    private MongoDatabase db;
    private MongoCollection<Document> employees;

    public Processor(MongoDatabase db) {
        this.db = db;
        employees = db.getCollection("employees");
    }
    public void init() {
        Bson group = group("$sex", avg("age", "$age"));
        Bson sort = sort(ascending("age"));
        Bson limit = limit(3);
        ArrayList<Document> results = employees.aggregate(Arrays.asList(group, sort, limit)).into(new ArrayList<>());
        for (Document doc : results)
            System.out.println("aggregate " + doc.toJson());
    }
}
