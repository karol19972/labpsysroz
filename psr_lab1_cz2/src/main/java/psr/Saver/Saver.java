package psr.Saver;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import psr.HConfig;
import psr.models.Client;
import psr.models.Film;
import psr.models.Transaction;

import java.net.UnknownHostException;
import java.util.Date;
import java.util.Map;
import java.util.Random;

public class Saver {
    final private static Random r = new Random(System.currentTimeMillis());
    private Map<Long, Client> clients;
    private Map<Long, Film> films;
    private Map<Long, Transaction> transacitons;

    public void setInitialValues() throws UnknownHostException {
        Config config = HConfig.getConfig();
        HazelcastInstance instance = Hazelcast.newHazelcastInstance(config);
        clients = instance.getMap("clients");
        films = instance.getMap("films");
        transacitons = instance.getMap("transacitons");
        fillClients();
        fillFilms();
        fillTransactions();

    }

    private void fillClients(){
        Long key1 = (long) Math.abs(r.nextInt());
        Client client1 = new Client("Jan","Kowalski", 29,"11111111111");
        System.out.println("PUT " + key1 + " => " + client1);
        clients.put(key1, client1);
        Long key2 = (long) Math.abs(r.nextInt());
        Client client2 = new Client("Adam", "Nowak",12,"22222222222");
        System.out.println("PUT " + key2 + " => " + client2);
        clients.put(key2, client2);
        Long key3 = (long) Math.abs(r.nextInt());
        Client client3 = new Client("Ola", "Kot",18,"33333333333");
        System.out.println("PUT " + key3 + " => " + client3);
        clients.put(key3, client3);
    }

    private void fillFilms(){
        Long key1 = (long) Math.abs(r.nextInt());
        Film film1 = new Film("Film1",12);
        System.out.println("PUT " + key1 + " => " + film1);
        films.put(key1, film1);
        Long key2 = (long) Math.abs(r.nextInt());
        Film film2 = new Film("Film2",18);
        System.out.println("PUT " + key2 + " => " + film2);
        films.put(key2, film2);
        Long key3 = (long) Math.abs(r.nextInt());
        Film film3 = new Film("Film3", 21);
        System.out.println("PUT " + key3 + " => " + film3);
        films.put(key3, film3);
    }

    private void fillTransactions(){
        Long key1 = (long) Math.abs(r.nextInt());
        Transaction transaction1 = new Transaction(12.99,new Date(2020,2,7),
                new Date(2021,1,1),"Film1","22222222222");
        System.out.println("PUT " + key1 + " => " + transaction1);
        transacitons.put(key1,transaction1);
        Long key2 = (long) Math.abs(r.nextInt());
        Transaction transaction2 = new Transaction(19.99,new Date(2020,4,12),
                new Date(2021,3,2),"Film3","11111111111");
        System.out.println("PUT " + key1 + " => " + transaction1);
        transacitons.put(key2,transaction2);
        Long key3 = (long) Math.abs(r.nextInt());
        Transaction transaction3 = new Transaction(13.99,new Date(2020,2,9),
                new Date(2021,1,1),"Film3","11111111111");
        System.out.println("PUT " + key3 + " => " + transaction3);
        transacitons.put(key3,transaction3);
    }


}
