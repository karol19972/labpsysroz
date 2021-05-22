package models;

import java.io.Serializable;
import java.util.Date;

public class Transaction implements Serializable {
    private String price;
    private String filmName;
    private String clientNumber;

    public Transaction(String price, String filmName, String clientNumber) {
        this.price = price;
        this.filmName = filmName;
        this.clientNumber = clientNumber;
    }

    public String getFilmName() {
        return filmName;
    }

    public void setFilmName(String filmName) {
        this.filmName = filmName;
    }

    public String getClientNumber() {
        return clientNumber;
    }

    public void setClientNumber(String clientNumber) {
        this.clientNumber = clientNumber;
    }


    public String getPrice() {
        return price;
    }

    public void setPrice(String price) {
        this.price = price;
    }

    @Override
    public String toString() {
        return "Transaction{" +
                "price='" + price + '\'' +
                ", filmName='" + filmName + '\'' +
                ", clientNumber='" + clientNumber + '\'' +
                '}';
    }
}
