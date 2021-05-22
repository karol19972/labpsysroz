import models.Client;
import models.Film;
import models.Transaction;
import oracle.kv.KVStore;
import oracle.kv.Key;
import oracle.kv.Value;
import oracle.kv.ValueVersion;

import java.util.ArrayList;
import java.util.Scanner;

public class Updater {
    KVStore kvStore;
    ArrayList<Film> films;
    ArrayList<Client> clients;
    ArrayList<Transaction> transactions;
    ArrayList<String> clientFieldsName;
    ArrayList<String> transactionFieldsName;
    ArrayList<String> filmFieldsName;

    public Updater(KVStore kvStore, ArrayList<Film> films, ArrayList<Client> clients, ArrayList<Transaction> transactions, ArrayList<String> clientFieldsName, ArrayList<String> transactionFieldsName, ArrayList<String> filmFieldsName) {
        this.kvStore = kvStore;
        this.films = films;
        this.clients = clients;
        this.transactions = transactions;
        this.clientFieldsName = clientFieldsName;
        this.transactionFieldsName = transactionFieldsName;
        this.filmFieldsName = filmFieldsName;
    }

    public void changeName(){
        System.out.println("Wpisz klucz");
        Scanner scanner = new Scanner(System.in);
        String s = scanner.nextLine();
        s = s.replace("/", "");
        String[] str = s.split("-");
        if (str.length == 2) {
            Key mKey = Key.createKey(str[0], str[1]);
            ValueVersion vval = kvStore.get(mKey);
            if (vval != null) {
                Value v = vval.getValue();
                String realValue = new String(v.getValue());
                System.out.println("Wpisz nowe imie");
                s = scanner.nextLine();
                Value myValue = Value.createValue(s.getBytes());
                kvStore.put(mKey, myValue);
                System.out.println(mKey + " - Zmieniono: "+ realValue +" na " + new String(myValue.getValue()));
            } else {
                System.out.println("Nieprawidlowy klucz");
            }
        } else {
            System.out.println("Nieprawidlowy klucz");
        }
    }
}
