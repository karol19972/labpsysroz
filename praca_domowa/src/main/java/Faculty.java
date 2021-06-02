import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;

@DynamoDBTable(tableName="Faculties")
public class Faculty {
    private int id;
    private String name;
    private int student;
    private int employee;

    public Faculty() {
    }

    public Faculty(int id, String name, int student, int employee) {
        this.id = id;
        this.name = name;
        this.student = student;
        this.employee = employee;
    }

    @DynamoDBHashKey(attributeName="id")
    public int getId() {
        return id;
    }
    public void setId(int id) {
        this.id = id;
    }
    @DynamoDBAttribute(attributeName="name")
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
    @DynamoDBAttribute(attributeName="student")
    public int getStudent() {
        return student;
    }

    public void setStudent(int student) {
        this.student = student;
    }
    @DynamoDBAttribute(attributeName="employee")
    public int getEmployee() {
        return employee;
    }

    public void setEmployee(int employee) {
        this.employee = employee;
    }

    @Override
    public String toString() {
        return "Faculty{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", student=" + student +
                ", employee=" + employee +
                '}';
    }
}
