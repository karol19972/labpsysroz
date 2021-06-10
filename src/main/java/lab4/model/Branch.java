package lab4.model;


import org.neo4j.ogm.annotation.*;

import java.util.HashSet;
import java.util.Set;


@NodeEntity(label = "Branch")
public class Branch {
    @Id
    @GeneratedValue
    private Long id;

    @Property(name = "city")
    private String city;

    @Relationship(type = "BRANCH_CAR")
    private Set<Car> cars = new HashSet<>();

    public void addCar(Car car) {
        cars.add(car);
    }

    @Relationship(type = "BRANCH_EMPLOYEE")
    private Set<Employee> employees = new HashSet<>();

    public void addEmployee(Employee employee) {
        employees.add(employee);
    }


    public Branch() {
    }

    public Branch(String city) {
        this.city = city;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }


    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    @Override
    public String toString() {
        return "Branch{" +
                "id=" + id +
                ", city='" + city + '\'' +
                ", cars=" + cars +
                ", employees=" + employees +
                '}';
    }
}
