package Models;

import java.io.Serializable;

public class Branch implements Serializable {
    private int id;
    private int car_id;
    private int employee_id;
    private String city;

    public Branch() {
    }

    public Branch(int id, int car_id, int employee_id, String city) {
        this.id = id;
        this.car_id = car_id;
        this.employee_id = employee_id;
        this.city = city;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getCar_id() {
        return car_id;
    }

    public void setCar_id(int car_id) {
        this.car_id = car_id;
    }

    public int getEmployee_id() {
        return employee_id;
    }

    public void setEmployee_id(int employee_id) {
        this.employee_id = employee_id;
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
                ", car_id=" + car_id +
                ", employee_id=" + employee_id +
                ", city='" + city + '\'' +
                '}';
    }
}
