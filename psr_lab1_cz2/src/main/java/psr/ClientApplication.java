package psr;

import psr.Cleaner.Cleaner;
import psr.HMapGetter.HMapGet;
import psr.Processor.Processor;
import psr.Saver.Saver;
import psr.Updater.Updater;

import java.net.UnknownHostException;
import java.util.Scanner;

public class ClientApplication {
    public static void main(String[] args) throws UnknownHostException {
        System.out.println("[0] quit\n[1] save\n[2] update\n[3] delete\n[4] fetch\n[5] process\n");
        Scanner scan = new Scanner(System.in);
        String operation = scan.nextLine();
        do{
            switch (operation){
                case "0":
                    System.out.println("Program zakończono pomyślnie");
                    break;
                case "1":
                    Saver saver = new Saver();
                    saver.setInitialValues();
                    break;
                case "2":
                    Updater updater = new Updater();
                    updater.init();
                    break;
                case "3":
                    Cleaner cleaner = new Cleaner();
                    cleaner.init();
                    break;
                case "4":
                    HMapGet hMapGet = new HMapGet();
                    hMapGet.getValues();
                    break;
                case "5":
                    Processor processor = new Processor();
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
