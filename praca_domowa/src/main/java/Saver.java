import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;

public class Saver {
    DynamoDBMapper mapper;
    public Saver(DynamoDBMapper mapper) {
        this.mapper = mapper;
    }

    public void setInitialValues() {
        saveEmployees();
        saveStudents();
        saveFaculties();
    }

    private void saveFaculties(){
        Faculty faculty1 = new Faculty(1,"Wydzial eletrotechniki, automatyki i robotyki",2,2);
        Faculty faculty2 = new Faculty(1,"Wydzial eletrotechniki, automatyki i robotyki",3,3);
        mapper.save(faculty1);
        mapper.save(faculty2);
    }

    private void saveStudents() {
        Student student1 = new Student(1,"Kubiak","Antoni","Informatyka");
        Student student2 = new Student(2,"Borkowski","Kamil","Automatyka i Robotyka");
        Student student3 = new Student(3,"Lis","Ola","Budownictwo");
        Student student4 = new Student(4,"Kot","Paulina","Budownictwo");
        mapper.save(student1);
        mapper.save(student2);
        mapper.save(student3);
        mapper.save(student4);
    }

    private void saveEmployees(){
        Employee employee1 = new Employee();
        employee1.setId(1);
        employee1.setFirstName("Alicja");
        employee1.setLastname("Kowalska");
        employee1.setSex("Kobieta");
        employee1.setAge(32);
        Employee employee2 = new Employee();
        employee2.setId(2);
        employee2.setFirstName("Robert");
        employee2.setLastname("Jankowski");
        employee2.setSex("Mezczyzna");
        employee2.setAge(46);
        Employee employee3 = new Employee();
        employee3.setId(3);
        employee3.setFirstName("Stanislaw");
        employee3.setLastname("Witkowski");
        employee3.setSex("Mezczyzna");
        employee3.setAge(54);
        Employee employee4 = new Employee();
        employee4.setId(4);
        employee4.setFirstName("Ania");
        employee4.setLastname("Markowska");
        employee4.setSex("Kobieta");
        employee4.setAge(23);
        mapper.save(employee1);
        mapper.save(employee2);
        mapper.save(employee3);
        mapper.save(employee4);
    }

}
