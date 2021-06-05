import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.CqlSession;
import menager.KeyspaceManager;

import java.util.Scanner;

public class Main {
    public static void main(String [] args){
        try (CqlSession session = CqlSession.builder().build()) {
            KeyspaceManager keyspaceManager = new KeyspaceManager(session, "transport");
            keyspaceManager.dropKeyspace();
            keyspaceManager.selectKeyspaces();
            keyspaceManager.createKeyspace();
            keyspaceManager.useKeyspace();
            TableCreator tableManager = new TableCreator(session);
            tableManager.createTable();
            System.out.println("[0] quit\n[1] save\n[2] update\n[3] delete\n[4] fetch\n[5] process\n");
            Scanner scan = new Scanner(System.in);
            String operation = scan.nextLine();
            do{
                switch (operation){
                    case "0":
                        System.out.println("Program zakończono pomyślnie");
                        break;
                    case "1":
                        Saver saver = new Saver(session);
                        saver.setInitialValues();
                        break;
                    case "2":
                        Updater updater = new Updater(session);
                        updater.init();
                        break;
                    case "3":
                        Cleaner cleaner = new Cleaner(session);
                        cleaner.init();
                        break;
                    case "4":
                        Fetcher fetcher = new Fetcher(session);
                        fetcher.getValues();
                        break;
                    case "5":
                        Processor processor = new Processor(session);
                        processor.init();
                        break;
                    default:
                        System.out.println("Podałeś nieprawidlowa operacje! Wybierz jadna z podanych");
                        break;
                }
                if(!operation.equals("0")) {
                    System.out.println("[0] quit\n[1] save\n[2] update\n[3] delete\n[4] fetch\n[5] process\n");
                    operation = scan.nextLine();
                }
            }while (!operation.equals("0"));


        }
    }
}
