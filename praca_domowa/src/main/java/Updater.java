import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;

public class Updater {
    DynamoDBMapper mapper;
    public Updater(DynamoDBMapper mapper) {
        this.mapper = mapper;
    }

    public void init() {
        System.out.println("zmieniam imie pierwszego studenta na Kasia");
        Student student = mapper.load(Student.class, 1);
        student.setFirstName("Kasia");
        mapper.save(student,
                new DynamoDBMapperConfig(
                        DynamoDBMapperConfig.SaveBehavior.CLOBBER));
    }
}
