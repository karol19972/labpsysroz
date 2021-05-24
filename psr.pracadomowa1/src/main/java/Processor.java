import models.Client;
import models.Film;
import models.Transaction;
import oracle.kv.KVStore;
import oracle.kv.Key;
import oracle.kv.Value;
import oracle.kv.ValueVersion;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Scanner;
import java.util.stream.Collectors;

public class Processor {
    KVStore kvStore;
    ArrayList<Film> films;
    ArrayList<Client> clients;
    ArrayList<Transaction> transactions;
    ArrayList<String> clientFieldsName;
    ArrayList<String> transactionFieldsName;
    ArrayList<String> filmFieldsName;

    public Processor(KVStore kvStore, ArrayList<Film> films, ArrayList<Client> clients, ArrayList<Transaction> transactions, ArrayList<String> clientFieldsName, ArrayList<String> transactionFieldsName, ArrayList<String> filmFieldsName) {
        this.kvStore = kvStore;
        this.films = films;
        this.clients = clients;
        this.transactions = transactions;
        this.clientFieldsName = clientFieldsName;
        this.transactionFieldsName = transactionFieldsName;
        this.filmFieldsName = filmFieldsName;
    }


    public void init() {

        process();
    }

    private void process() {
        ArrayList<Client> realClients = new ArrayList<>();
        ArrayList<Transaction> realTransactions = new ArrayList<>();
        realClients.clear();
        realTransactions.clear();
        setAllClients(realClients);
        setAllTransactions(realTransactions);
        ArrayList<BigDecimal> ages = new ArrayList<>();
        ArrayList<BigDecimal> prices = new ArrayList<>();
        for(Client c: realClients){
            ages.add(new BigDecimal(c.getAge()));
        }
        for(Transaction c: realTransactions){
            prices.add(new BigDecimal(c.getPrice()));
        }
        BigDecimal sumAge = new BigDecimal(0);
        BigDecimal sumPrice = new BigDecimal(0);
        for (BigDecimal d : ages)
            sumAge = sumAge.add(d);
        for (BigDecimal d : prices)
            sumPrice = sumPrice.add(d);
        System.out.println("Średnia wieku klientow to: "+(sumAge.doubleValue()/ages.size()));
        System.out.println("Średnia cena transakcji to: "+(sumPrice.doubleValue()/prices.size()));
    }

    private void setAllClients(ArrayList<Client> realClients){
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
            BigDecimal realValueAge = null;
            if (vval != null) {
                v = vval.getValue();
                realValueAge = new BigDecimal(new String(v.getValue()));
            }
            mKey = Key.createKey("Client" + (i + 1), clientFieldsName.get(3));
            vval = kvStore.get(mKey);
            String realValue4 = null;
            if (vval != null) {
                v = vval.getValue();
                realValue4 = new String(v.getValue());
            }
            if(realValue!=null&&realValue2!=null&&realValueAge!=null&&realValue4!=null){
                Client client = new Client(realValue,realValue2,realValueAge.toString(),realValue4);
                realClients.add(client);
            }


        }
    }

    private void setAllTransactions(ArrayList<Transaction> realTransactions){
        for (int i = 0; i < transactions.size(); i++) {
            Key mKey = Key.createKey("Transaction" + (i + 1), transactionFieldsName.get(0));
            ValueVersion vval = kvStore.get(mKey);
            Value v;
            String realValue = null;
            if (vval != null) {
                v = vval.getValue();
                realValue = new String(v.getValue());
            }
            mKey = Key.createKey("Transaction" + (i + 1), transactionFieldsName.get(1));
            vval = kvStore.get(mKey);
            String realValue2 = null;
            if (vval != null) {
                v = vval.getValue();
                realValue2 = new String(v.getValue());
            }

            mKey = Key.createKey("Transaction" + (i + 1), transactionFieldsName.get(2));
            vval = kvStore.get(mKey);
            String realValue3 = null;
            if (vval != null) {
                v = vval.getValue();
                realValue3 = new String(v.getValue());
            }
            if(realValue!=null&&realValue2!=null&&realValue3!=null){
                Transaction transaction = new Transaction(realValue,realValue2,realValue3);
                realTransactions.add(transaction);
            }


        }
    }
}
