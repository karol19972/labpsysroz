package models;

import java.io.Serializable;
import java.util.List;

public class Client implements Serializable {
    private String name;
    private String surname;
    private String age;
    private String PESEL;

    public Client(String name, String surname, String age, String PESEL) {
        this.name = name;
        this.surname = surname;
        this.age = age;
        this.PESEL = PESEL;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSurname() {
        return surname;
    }

    public void setSurname(String surname) {
        this.surname = surname;
    }

    public String getAge() {
        return age;
    }

    public void setAge(String age) {
        this.age = age;
    }

    public String getPESEL() {
        return PESEL;
    }

    public void setPESEL(String PESEL) {
        this.PESEL = PESEL;
    }

    @Override
    public String toString() {
        return "Client{" +
                "name='" + name + '\'' +
                ", surname='" + surname + '\'' +
                ", age=" + age +
                ", PESEL='" + PESEL + '\'' +
                '}';
    }
}
