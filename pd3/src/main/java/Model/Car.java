package Model;

public class Car {
    private String partitionKey;
    private String id;
    private String mark;
    private String model;
    private int year;

    public Car() {
    }

    public Car(String partitionKey, String id, String mark, String model, int year) {
        this.partitionKey = partitionKey;
        this.id = id;
        this.mark = mark;
        this.model = model;
        this.year = year;
    }

    public String getPartitionKey() {
        return partitionKey;
    }

    public void setPartitionKey(String partitionKey) {
        this.partitionKey = partitionKey;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getMark() {
        return mark;
    }

    public void setMark(String mark) {
        this.mark = mark;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    @Override
    public String toString() {
        return "Car{" +
                "partitionKey='" + partitionKey + '\'' +
                ", id='" + id + '\'' +
                ", mark='" + mark + '\'' +
                ", model='" + model + '\'' +
                ", year=" + year +
                '}';
    }
}
