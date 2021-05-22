import models.Client;
import models.Film;
import models.Transaction;
import oracle.kv.KVStore;
import oracle.kv.Key;
import oracle.kv.Value;
import oracle.kv.ValueVersion;

import java.util.ArrayList;
import java.util.Scanner;

public class Cleaner {
    KVStore kvStore;
    ArrayList<Film> films;
    ArrayList<Client> clients;
    ArrayList<Transaction> transactions;
    ArrayList<String> clientFieldsName;
    ArrayList<String> transactionFieldsName;
    ArrayList<String> filmFieldsName;

    public Cleaner(KVStore kvStore, ArrayList<Film> films, ArrayList<Client> clients, ArrayList<Transaction> transactions, ArrayList<String> clientFieldsName, ArrayList<String> transactionFieldsName, ArrayList<String> filmFieldsName) {
        this.kvStore = kvStore;
        this.films = films;
        this.clients = clients;
        this.transactions = transactions;
        this.clientFieldsName = clientFieldsName;
        this.transactionFieldsName = transactionFieldsName;
        this.filmFieldsName = filmFieldsName;
    }

    public void clearAll(){
        String s = null;
        for (int i = 0; i < clients.size(); i++) {
            Key mKey = Key.createKey("Client" + (i + 1), clientFieldsName.get(0));
            kvStore.delete(mKey);
            mKey = Key.createKey("Client" + (i + 1), clientFieldsName.get(1));
            kvStore.delete(mKey);
            mKey = Key.createKey("Client" + (i + 1), clientFieldsName.get(2));
            kvStore.delete(mKey);
            mKey = Key.createKey("Client" + (i + 1), clientFieldsName.get(3));
            kvStore.delete(mKey);

        }
        for (int i = 0; i < films.size(); i++) {
            Key mKey = Key.createKey("Film" + (i + 1), filmFieldsName.get(0));
            kvStore.delete(mKey);
            mKey = Key.createKey("Film" + (i + 1), filmFieldsName.get(1));
            kvStore.delete(mKey);
        }
        for (int i = 0; i < clients.size(); i++) {
            Key mKey = Key.createKey("Transaction" + (i + 1), transactionFieldsName.get(0));
            kvStore.delete(mKey);
            mKey = Key.createKey("Transaction" + (i + 1), transactionFieldsName.get(1));
            kvStore.delete(mKey);
            mKey = Key.createKey("Transaction" + (i + 1), transactionFieldsName.get(2));
            kvStore.delete(mKey);
        }
        System.out.println("Wyczyszczono wszystko");
    }
}
