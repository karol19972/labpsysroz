import models.Client;
import models.Film;
import models.Transaction;
import oracle.kv.KVStore;
import oracle.kv.Key;
import oracle.kv.Value;

import java.util.ArrayList;

public class Saver {
    ArrayList<Film> films;
    ArrayList<Client> clients;
    ArrayList<Transaction> transactions;
    ArrayList<String> clientFieldsName;
    ArrayList<String> transactionFieldsName;
    ArrayList<String> filmFieldsName;
    KVStore store;

    public Saver(KVStore store, ArrayList<Client> clients, ArrayList<Film> films, ArrayList<Transaction> transactions,
                 ArrayList<String> clientFieldsName, ArrayList<String> transactionFieldsName, ArrayList<String> filmFieldsName) {
        this.clients = clients;
        this.films = films;
        this.transactions = transactions;
        this.clientFieldsName = clientFieldsName;
        this.transactionFieldsName = transactionFieldsName;
        this.filmFieldsName = filmFieldsName;
        this.store = store;
    }

    public ArrayList<String> getClientFieldsName() {
        return clientFieldsName;
    }

    public ArrayList<String> getTransactionFieldsName() {
        return transactionFieldsName;
    }

    public ArrayList<String> getFilmFieldsName() {
        return filmFieldsName;
    }

    public ArrayList<Film> getFilms() {
        return films;
    }

    public ArrayList<Client> getClients() {
        return clients;
    }

    public ArrayList<Transaction> getTransactions() {
        return transactions;
    }

    public void fillTables(){
        clients.clear();
        films.clear();
        transactions.clear();
        fillClients();
        fillFilms();
        fillTransactions();
    }
    public void setInitialValues() {
        fillTables();
        save(clients.get(0));
        save(films.get(0));
        save(transactions.get(0));

    }

    private void fillClients() {
        Client client1 = new Client("Jan","Kowalski", "29","11111111111");
        Client client2 = new Client("Adam", "Nowak","12","22222222222");
        Client client3 = new Client("Ola", "Kot","18","33333333333");
        clients.add(client1);
        clients.add(client2);
        clients.add(client3);
    }

    private void fillFilms() {
        Film film1 = new Film("Film1","12");
        Film film2 = new Film("Film2","18");
        Film film3 = new Film("Film3", "21");
        films.add(film1);
        films.add(film2);
        films.add(film3);
    }

    private void fillTransactions(){
        Transaction transaction1 = new Transaction("12.99","Film1","22222222222");
        Transaction transaction2 = new Transaction("19.99","Film3","11111111111");
        Transaction transaction3 = new Transaction("13.99","Film3","11111111111");
        transactions.add(transaction1);
        transactions.add(transaction2);
        transactions.add(transaction3);
    }

    private void save(Object o){
        Key mKey = null;
        if(o instanceof Client){
            for(int i=0;i<clients.size();i++) {
                mKey = Key.createKey("Client"+(i+1), clientFieldsName.get(0));
                Value myValue = Value.createValue(clients.get(i).getName().getBytes());
                store.put(mKey, myValue);
                mKey = Key.createKey("Client"+(i+1), clientFieldsName.get(1));
                myValue = Value.createValue(clients.get(i).getSurname().getBytes());
                store.put(mKey, myValue);
                mKey = Key.createKey("Client"+(i+1), clientFieldsName.get(2));
                myValue = Value.createValue(clients.get(i).getAge().getBytes());
                store.put(mKey, myValue);
                mKey = Key.createKey("Client"+(i+1), clientFieldsName.get(3));
                myValue = Value.createValue(clients.get(i).getPESEL().getBytes());
                store.put(mKey, myValue);
            }
        }
        if(o instanceof Transaction){
            for(int i=0;i<transactions.size();i++) {
                mKey = Key.createKey("Transaction"+(i+1), transactionFieldsName.get(0));
                Value myValue = Value.createValue(transactions.get(i).getPrice().getBytes());
                store.put(mKey, myValue);
                mKey = Key.createKey("Transaction"+(i+1), transactionFieldsName.get(1));
                myValue = Value.createValue(transactions.get(i).getFilmName().getBytes());
                store.put(mKey, myValue);
                mKey = Key.createKey("Transaction"+(i+1), transactionFieldsName.get(2));
                myValue = Value.createValue(transactions.get(i).getClientNumber().getBytes());
                store.put(mKey, myValue);

            }
        }
        if(o instanceof Film){
            for(int i=0;i<films.size();i++) {
                mKey = Key.createKey("Film"+(i+1), filmFieldsName.get(0));
                Value myValue = Value.createValue(films.get(i).getName().getBytes());
                store.put(mKey, myValue);
                mKey = Key.createKey("Film"+(i+1), filmFieldsName.get(1));
                myValue = Value.createValue(films.get(i).getFromAge().getBytes());
                store.put(mKey, myValue);
            }
        }
    }

}
