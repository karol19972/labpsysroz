package Model;

public class Route {
    private String partitionKey;
    private String id;
    private String name;
    private int car;
    private int transporter;

    public Route() {
    }

    public Route(String partitionKey, String id, String name, int car, int transporter) {
        this.partitionKey = partitionKey;
        this.id = id;
        this.name = name;
        this.car = car;
        this.transporter = transporter;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getCar() {
        return car;
    }

    public void setCar(int car) {
        this.car = car;
    }

    public int getTransporter() {
        return transporter;
    }

    public void setTransporter(int transporter) {
        this.transporter = transporter;
    }

    @Override
    public String toString() {
        return "Route{" +
                "partitionKey='" + partitionKey + '\'' +
                ", id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", car=" + car +
                ", transporter=" + transporter +
                '}';
    }
}
