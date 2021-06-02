import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;

@DynamoDBTable(tableName="Student")
public class Student {
    private int id;
    private String lastname;
    private String firstName;
    private String fieldOfStudy;

    public Student(int id, String lastname, String firstName, String fieldOfStudy) {
        this.id = id;
        this.lastname = lastname;
        this.firstName = firstName;
        this.fieldOfStudy = fieldOfStudy;
    }

    public Student() {
    }

    @DynamoDBHashKey(attributeName="id")
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    @DynamoDBAttribute(attributeName="lastname")
    public String getLastname() {
        return lastname;
    }

    public void setLastname(String lastname) {
        this.lastname = lastname;
    }

    @DynamoDBAttribute(attributeName="firstname")
    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    @DynamoDBAttribute(attributeName="fieldOfStudy")
    public String getFieldOfStudy() {
        return fieldOfStudy;
    }

    public void setFieldOfStudy(String fieldOfStudy) {
        this.fieldOfStudy = fieldOfStudy;
    }

    @Override
    public String toString() {
        return "Student{" +
                "id=" + id +
                ", lastname='" + lastname + '\'' +
                ", firstName='" + firstName + '\'' +
                ", fieldOfStudy='" + fieldOfStudy + '\'' +
                '}';
    }
}
