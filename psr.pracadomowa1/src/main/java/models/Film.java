package models;

import java.io.Serializable;

public class Film implements Serializable {
    private String name;
    private String fromAge;

    public Film(String name, String fromAge) {
        this.name = name;
        this.fromAge = fromAge;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFromAge() {
        return fromAge;
    }

    public void setFromAge(String fromAge) {
        this.fromAge = fromAge;
    }

    @Override
    public String toString() {
        return "Film{" +
                "name='" + name + '\'' +
                ", fromAge=" + fromAge +
                '}';
    }
}
