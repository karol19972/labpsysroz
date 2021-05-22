package psr.Cleaner;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import psr.HConfig;
import psr.Saver.Saver;
import psr.models.Client;
import psr.models.Film;
import psr.models.Transaction;

import java.net.UnknownHostException;
import java.util.Map;
import java.util.Scanner;

public class Cleaner {
    private Map<Long, Client> clients;
    private Map<Long, Film> films;
    private Map<Long, Transaction> transacitons;

    public void init() throws UnknownHostException {
        Config config = HConfig.getConfig();
        HazelcastInstance instance = Hazelcast.newHazelcastInstance(config);
        clients = instance.getMap("clients");
        films = instance.getMap("films");
        transacitons = instance.getMap("transacitons");
        String operation;
        do{
            System.out.println("[1] Usuń klienta\n[2] Usuń film\n[3] Usuń transakcje\n[4] Usuń wszystko");
            Scanner scan = new Scanner(System.in);
            operation = scan.nextLine();
            switch (operation){
                case "1":
                    deleteClient();
                    break;
                case "2":
                    deleteFilm();
                    break;
                case "3":
                    deleteTransaction();
                    break;
                case "4":
                    clearAll();
                    break;
            }
        }while (!(operation.equals("1")||operation.equals("2")||operation.equals("3")||operation.equals("4")));
    }


    public void deleteFilm() {
        System.out.println("Podaj tytul filmu: ");
        Scanner scanner = new Scanner(System.in);
        String title = scanner.nextLine();
        long id = -1;
        for (Map.Entry<Long, Film> entry : films.entrySet()) {
            if (entry.getValue().getName().equals(title)) {
                id = entry.getKey();
                break;
            }
        }
        if (id != -1) {
            System.out.println("Usunieto film: " + films.get(id));
            films.remove(id);

        } else {
            System.out.println("Nieprawidlowy tytul filmu");
        }
    }

    public void clearAll(){
        clients.clear();
        transacitons.clear();
        films.clear();
    }

    public void deleteClient() {
        System.out.println("Podaj PESEL klienta: ");
        Scanner scanner = new Scanner(System.in);
        String PESEL = scanner.nextLine();
        long id = -1;
        for (Map.Entry<Long, Client> entry : clients.entrySet()) {
            if (entry.getValue().getPESEL().equals(PESEL)) {
                id = entry.getKey();
                break;
            }
        }
        if (id != -1) {
            System.out.println("Usunieto klienta: " + clients.get(id));
            clients.remove(id);
        } else {
            System.out.println("Nieprawidlowy PESEL");
        }
    }

    public void deleteTransaction() {
        String operation;
        do{
            System.out.println("[1] Usuń po PESELu klienta\n[2]Usuń po nazwie filmu");
            Scanner scan = new Scanner(System.in);
            operation = scan.nextLine();
            switch (operation){
                case "1":
                    System.out.println("Podaj PESEL klienta: ");
                    Scanner scanner = new Scanner(System.in);
                    String PESEL = scanner.nextLine();
                    long id = -1;
                    for (Map.Entry<Long, Transaction> entry : transacitons.entrySet()) {
                        if (entry.getValue().getClientNumber().equals(PESEL)) {
                            id = entry.getKey();
                            break;
                        }
                    }
                    if (id != -1) {
                        System.out.println("Usunieto transakcje: " + transacitons.get(id));
                        transacitons.remove(id);
                    } else {
                        System.out.println("Nieprawidlowy PESEL");
                    }
                    break;
                case "2":
                    System.out.println("Podaj nazwe filmu: ");
                    Scanner scanner2 = new Scanner(System.in);
                    String name = scanner2.nextLine();
                    long id2 = -1;
                    for (Map.Entry<Long, Transaction> entry : transacitons.entrySet()) {
                        if (entry.getValue().getFilmName().equals(name)) {
                            id2 = entry.getKey();
                            break;
                        }
                    }
                    if (id2 != -1) {
                        System.out.println("Usunieto transakcje: " + transacitons.get(id2));
                        transacitons.remove(id2);
                    } else {
                        System.out.println("Nieprawidlowa nazwa filmu");
                    }
                    break;
                default:
                    System.out.println("Podałeś nieprawidlowa operacje! Wybierz jadna z podanych");
                    break;
            }
        }while (!(operation.equals("1")||operation.equals("2")));
    }

}
