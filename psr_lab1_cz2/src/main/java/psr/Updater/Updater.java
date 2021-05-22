package psr.Updater;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import psr.HConfig;
import psr.models.Client;
import psr.models.Film;

import java.net.UnknownHostException;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;

public class Updater {
    final private static Random r = new Random(System.currentTimeMillis());
    private Map<Long, Client> clients;
    private Map<Long, Film> films;
    public void init() throws UnknownHostException {
        Config config = HConfig.getConfig();
        HazelcastInstance instance = Hazelcast.newHazelcastInstance(config);
        clients = instance.getMap("clients");
        films = instance.getMap("films");
        String operation;
        do{
            System.out.println("[1] Aktualizuj klienta\n[2] Aktualizuj film\n");
            Scanner scan = new Scanner(System.in);
            operation = scan.nextLine();
            switch (operation){
                case "1":
                    updateClient();
                    break;
                case "2":
                    updateFilm();
                    break;
            }
        }while (!(operation.equals("1")||operation.equals("2")));
    }

    private void updateFilm() {
        long id = -1;
        String operation;
        System.out.println("Wpisz nazwe filmu");
        Scanner scan = new Scanner(System.in);
        operation = scan.nextLine();
        for(Map.Entry<Long, Film> e : films.entrySet()){
            if(e.getValue().getName().equals(operation)) {
                id = e.getKey();
                break;
            }
        }
        if(films.get(id)!=null){
            Film film = updateFilmExec(films.get(id));
            films.replace(id,film);
            System.out.println("Film zostal zmieniony");
        }else {
            System.out.println("Nieprawidlowa nazwa Filmu");
        }
    }

    private Film updateFilmExec(Film film) {
        String operation;
        do{
            System.out.println("[1] Aktualizuj nazwe filmu\n[2] Aktualizuj wiek dozwolony\n");
            Scanner scan = new Scanner(System.in);
            operation = scan.nextLine();
            switch (operation){
                case "1":
                    System.out.println("Podaj nazwe");
                    String surname;
                    scan = new Scanner(System.in);
                    surname = scan.nextLine();
                    film.setName(surname);
                    break;
                case "2":
                    System.out.println("Podaj wiek");
                    int age;
                    scan = new Scanner(System.in);
                    age = scan.nextInt();
                    film.setFromAge(age);
                    break;
            }
        }while (!(operation.equals("1")||operation.equals("2")));
        return film;
    }


    private void updateClient() {
        long id = -1;
        String operation;
        System.out.println("Wpisz PESEL");
        Scanner scan = new Scanner(System.in);
        operation = scan.nextLine();
        for(Map.Entry<Long, Client> e : clients.entrySet()){
            if(e.getValue().getPESEL().equals(operation)) {
                id = e.getKey();
                break;
            }
        }
        if(clients.get(id)!=null){
            Client client = updateClientExec(clients.get(id));
            clients.replace(id,client);
            System.out.println("Klient zostal zmieniony");
        }else {
            System.out.println("Nieprawidlowy PESEL");
        }
    }

    public Client updateClientExec(Client client){
        String operation;
        do{
            System.out.println("[1] Aktualizuj imie\n[2] Aktualizuj nazwisko\n[3] Aktualizuj PESEL\n[4] Aktualizuj wiek");
            Scanner scan = new Scanner(System.in);
            operation = scan.nextLine();
            switch (operation){
                case "1":
                    System.out.println("Podaj imie");
                    String surname;
                    scan = new Scanner(System.in);
                    surname = scan.nextLine();
                    client.setName(surname);
                    break;
                case "2":
                    System.out.println("Podaj nazwisko");
                    String name;
                    scan = new Scanner(System.in);
                    name = scan.nextLine();
                    client.setSurname(name);
                    break;
                case "3":
                    System.out.println("Podaj PESEL");
                    String pesel;
                    scan = new Scanner(System.in);
                    pesel = scan.nextLine();
                    client.setPESEL(pesel);
                    break;
                case "4":
                    System.out.println("Podaj wiek");
                    int age;
                    scan = new Scanner(System.in);
                    age = scan.nextInt();
                    client.setAge(age);
                    break;
            }
        }while (!(operation.equals("1")||operation.equals("2")||operation.equals("3"))||operation.equals("4"));
        return client;
    }


}



