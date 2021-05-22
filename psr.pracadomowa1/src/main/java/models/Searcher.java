package models;

import oracle.kv.KVStore;
import oracle.kv.Key;
import oracle.kv.Value;
import oracle.kv.ValueVersion;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Searcher {

    KVStore kvStore;
    ArrayList<Film> films;
    ArrayList<Client> clients;
    ArrayList<Transaction> transactions;
    ArrayList<String> clientFieldsName;
    ArrayList<String> transactionFieldsName;
    ArrayList<String> filmFieldsName;

    public Searcher(KVStore kvStore, ArrayList<Film> films, ArrayList<Client> clients, ArrayList<Transaction> transactions,
                    ArrayList<String> clientFieldsName, ArrayList<String> transactionFieldsName, ArrayList<String> filmFieldsName) {
        this.kvStore = kvStore;
        this.films = films;
        this.clients = clients;
        this.transactions = transactions;
        this.clientFieldsName = clientFieldsName;
        this.transactionFieldsName = transactionFieldsName;
        this.filmFieldsName = filmFieldsName;
    }

    public void getValues() {
        String operation = null;
        do {
            System.out.println("[1] search all\n[2] search after key\n[3] search after attribute\n");
            Scanner scan = new Scanner(System.in);
            operation = scan.nextLine();
            switch (operation) {
                case "1":
                    getAllValuesFromTable();
                    break;
                case "2":
                    getRecordAfterKey();
                    break;
                case "3":
                    System.out.println("Zaakwansowane wyszukiwanie polega na tym, ze poda wszystkie osoby, " +
                            "ktore sa pelnoletnimi klientami");
                    getAdults();
                    break;
            }
        } while (!(operation.equals("1") || operation.equals("2") || operation.equals("3")));

    }

    public void getRecordAfterKey() {
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
                System.out.println(mKey + " => " + realValue);
            } else {
                System.out.println("Nieprawidlowy klucz");
            }
        } else {
            System.out.println("Nieprawidlowy klucz");
        }
    }

    public void getAllValuesFromTable() {
        String s = null;
        do {
            System.out.println("Wpisz nazwe tabeli");
            Scanner scanner = new Scanner(System.in);
            s = scanner.nextLine();
            if (s.toLowerCase().equals("client")) {
                System.out.println("Clients[");
                for (int i = 0; i < clients.size(); i++) {
                    Key mKey = Key.createKey("Client" + (i + 1), clientFieldsName.get(0));
                    System.out.println("{");
                    ValueVersion vval = kvStore.get(mKey);
                    Value v;
                    String realValue;
                    if (vval != null) {
                        v = vval.getValue();
                        realValue = new String(v.getValue());
                        System.out.println("key - " + mKey);
                        System.out.println(clientFieldsName.get(0) + " : " + realValue + ",");
                    } else {
                        System.out.println("");
                    }
                    mKey = Key.createKey("Client" + (i + 1), clientFieldsName.get(1));
                    vval = kvStore.get(mKey);
                    if (vval != null) {
                        v = vval.getValue();
                        realValue = new String(v.getValue());
                        System.out.println("key - " + mKey);
                        System.out.println(clientFieldsName.get(1) + " : " + realValue + ",");
                    } else {
                        System.out.println("");
                    }
                    mKey = Key.createKey("Client" + (i + 1), clientFieldsName.get(2));
                    vval = kvStore.get(mKey);
                    if (vval != null) {
                        v = vval.getValue();
                        realValue = new String(v.getValue());
                        System.out.println("key - " + mKey);
                        System.out.println(clientFieldsName.get(2) + " : " + realValue + ",");
                    } else {
                        System.out.println("");
                    }
                    mKey = Key.createKey("Client" + (i + 1), clientFieldsName.get(3));
                    vval = kvStore.get(mKey);
                    if (vval != null) {
                        v = vval.getValue();
                        realValue = new String(v.getValue());
                        System.out.println("key - " + mKey);
                        System.out.println(clientFieldsName.get(3) + " : " + realValue);
                    }
                    System.out.println("},");
                }
                System.out.println("]");

            } else if (s.toLowerCase().equals("film")) {
                System.out.println("Films[");
                for (int i = 0; i < films.size(); i++) {
                    Key mKey = Key.createKey("Film" + (i + 1), filmFieldsName.get(0));
                    System.out.println("{");
                    ValueVersion vval = kvStore.get(mKey);
                    Value v;
                    String realValue;
                    if (vval != null) {
                        v = vval.getValue();
                        realValue = new String(v.getValue());
                        System.out.println("key - " + mKey);
                        System.out.println(filmFieldsName.get(0) + " : " + realValue + ",");
                    }
                    mKey = Key.createKey("Film" + (i + 1), filmFieldsName.get(1));
                    vval = kvStore.get(mKey);
                    if (vval != null) {
                        v = vval.getValue();
                        realValue = new String(v.getValue());
                        System.out.println("key - " + mKey);
                        System.out.println(filmFieldsName.get(1) + " : " + realValue);

                        System.out.println("},");
                    }
                }
                System.out.println("]");
            } else if (s.toLowerCase().equals("transaction")) {
                System.out.println("Transaction[");
                for (int i = 0; i < clients.size(); i++) {
                    Key mKey = Key.createKey("Transaction" + (i + 1), transactionFieldsName.get(0));
                    System.out.println("{");
                    ValueVersion vval = kvStore.get(mKey);
                    Value v;
                    String realValue;
                    if (vval != null) {
                        v = vval.getValue();
                        realValue = new String(v.getValue());
                        System.out.println("key - " + mKey);
                        System.out.println(transactionFieldsName.get(0) + " : " + realValue + ",");
                    }
                    mKey = Key.createKey("Transaction" + (i + 1), transactionFieldsName.get(1));
                    vval = kvStore.get(mKey);
                    if (vval != null) {
                        v = vval.getValue();
                        realValue = new String(v.getValue());
                        System.out.println("key - " + mKey);
                        System.out.println(transactionFieldsName.get(1) + " : " + realValue + ",");
                    }
                    mKey = Key.createKey("Transaction" + (i + 1), transactionFieldsName.get(2));
                    vval = kvStore.get(mKey);
                    if (vval != null) {
                        v = vval.getValue();
                        realValue = new String(v.getValue());
                        System.out.println("key - " + mKey);
                        System.out.println(transactionFieldsName.get(2) + " : " + realValue);
                    }
                    System.out.println("},");
                }
                System.out.println("]");
            } else {
                System.out.println("Zla tabela");
            }
        } while (!(s.toLowerCase().equals("client") || s.toLowerCase().equals("film") || s.toLowerCase().equals("transaction")));
    }

    public void getAdults() {
        List<Client> adultClient = new ArrayList<>();

        for (int i = 0; i < clients.size(); i++) {
            Key mKey = Key.createKey("Client" + (i + 1), clientFieldsName.get(0));
            ValueVersion vval = kvStore.get(mKey);
            Value v;
            String realValue = null;
            if (vval != null) {
                v = vval.getValue();
                realValue = new String(v.getValue());
            }
            mKey = Key.createKey("Client" + (i + 1), clientFieldsName.get(1));
            vval = kvStore.get(mKey);
            String realValue2 = null;
            if (vval != null) {
                v = vval.getValue();
                realValue2 = new String(v.getValue());
            }

            mKey = Key.createKey("Client" + (i + 1), clientFieldsName.get(2));
            vval = kvStore.get(mKey);
            BigDecimal realValueAge = new BigDecimal(-1);
            if (vval != null) {
                v = vval.getValue();
                realValueAge = new BigDecimal(new String(v.getValue()));
            }
            mKey = Key.createKey("Client" + (i + 1), clientFieldsName.get(3));
            vval = kvStore.get(mKey);
            if (vval != null) {
                v = vval.getValue();
                String realValue4 = new String(v.getValue());
                if (realValueAge.doubleValue() >= 18) {
                    Client client = new Client(realValue, realValue2, realValueAge.toString(), realValue4);
                    adultClient.add(client);
                }
            }
            System.out.println(adultClient);

        }

    }
}
