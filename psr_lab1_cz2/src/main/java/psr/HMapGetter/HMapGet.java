package psr.HMapGetter;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import psr.HConfig;
import psr.models.Client;
import psr.models.Film;
import psr.models.Transaction;

import java.net.UnknownHostException;
import java.util.*;
import java.util.stream.Collectors;

public class HMapGet {
    final private static Random r = new Random(System.currentTimeMillis());
    IMap<Long, Client> clients;
    IMap<Long, Film> films;
    IMap<Long, Transaction> transacitons;
    public void getValues() throws UnknownHostException {
        ClientConfig clientConfig = HConfig.getClientConfig();
        HazelcastInstance client = HazelcastClient.newHazelcastClient( clientConfig );
        clients = client.getMap( "clients" );
        films = client.getMap( "films" );
        transacitons = client.getMap( "transacitons" );
        String operation;
        do{
            System.out.println("[1] Wyswietl wszystko\n[2] Wyszukaj po id\n[3] Wyszukaj po innych parametrach\n");
            Scanner scan = new Scanner(System.in);
            operation = scan.nextLine();
            switch (operation){
                case "1":
                    showAll();
                    break;
                case "2":
                    showAfterId();
                    break;
                case "3":
                    showAfterOtherParameter();
                    break;
            }
        }while (!(operation.equals("1")||operation.equals("2")||operation.equals("3")));
    }

    private void showAfterOtherParameter() {
        System.out.println("Złożone zapytanie bedzie polegalo na tym ze wyswiele wszystkie transakcje, " +
                "w ktorych klienci byli pelnoletni");
        List<String> PESELS = new ArrayList<>();
        for(Map.Entry<Long, Client> e : clients.entrySet()){
            if(e.getValue().getAge()>=18) PESELS.add(e.getValue().getPESEL());
        }
        Map<Long, Transaction> adultClientTransaction = transacitons.entrySet().stream()
                .filter(x ->
                    PESELS.contains(x.getValue().getClientNumber())
                )
                .collect(Collectors.toMap(x -> x.getKey(), x -> x.getValue()));
        System.out.println(adultClientTransaction);
    }


    private void showAll() {
        String operation;
        do{
            System.out.println("[1] Wyswietl klientow\n[2] Wyswietl filmy\n[3] Wyswietl transakcje");
            Scanner scan = new Scanner(System.in);
            operation = scan.nextLine();
            switch (operation){
                case "1":
                    System.out.println("All clients: ");
                    for(Map.Entry<Long, Client> e : clients.entrySet()){
                        System.out.println(e.getKey() + " => " + e.getValue());
                    }
                    break;
                case "2":
                    System.out.println("All films: ");
                    for(Map.Entry<Long, Film> e : films.entrySet()){
                        System.out.println(e.getKey() + " => " + e.getValue());
                    }
                    break;
                case "3":
                    System.out.println("All transactions: ");
                    for(Map.Entry<Long, Transaction> e : transacitons.entrySet()){
                        System.out.println(e.getKey() + " => " + e.getValue());
                    }
                    break;
            }
        }while (!(operation.equals("1")||operation.equals("2")||operation.equals("3")));
    }

    private void showAfterId() {
        String operation;
        long id;
        do{
            System.out.println("[1] Wyswietl klientow\n[2] Wyswietl filmy\n[3] Wyswietl transakcje");
            Scanner scan = new Scanner(System.in);
            operation = scan.nextLine();
            System.out.println("Podaj ID");
            scan = new Scanner(System.in);
            id = scan.nextLong();
            switch (operation){
                case "1":
                    if(clients.get(id)!=null){
                        System.out.println("Klient o id: "+id +" = " + clients.get(id));
                    }else {
                        System.out.println("Nie ma klienta o takim id");
                    }
                    break;
                case "2":
                    if(films.get(id)!=null){
                        System.out.println("Film o id: "+id +" = " + films.get(id));
                    }else {
                        System.out.println("Nie ma filmu o takim id");
                    }
                    break;
                case "3":
                    if(transacitons.get(id)!=null){
                        System.out.println("Transakcja o id: "+id +" = " + transacitons.get(id));
                    }else {
                        System.out.println("Nie ma transakcji o takim id");
                    }
                    break;
            }
        }while (!(operation.equals("1")||operation.equals("2")||operation.equals("3")));
    }
}
