import com.orientechnologies.orient.core.db.ODatabaseSession;
import com.orientechnologies.orient.core.db.OrientDB;
import com.orientechnologies.orient.core.db.OrientDBConfig;
import com.orientechnologies.orient.core.metadata.schema.OClass;
import com.orientechnologies.orient.core.metadata.schema.OType;

import java.util.Scanner;

public class Main {
    public static ODatabaseSession db;

    public static void main(String[] args){
        OrientDB orient = new OrientDB("remote:localhost", OrientDBConfig.defaultConfig());
        db = orient.open("test", "admin", "root");
        createTables();
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
                    Fetcher fetcher = new Fetcher();
                    fetcher.getValues();
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




        db.close();
        orient.close();
    }

    public static void createTables(){
        OClass car = db.getClass("Car");

        if (car == null) {
            car = db.createVertexClass("Car");
        }

        if (car.getProperty("id") == null) {
            car.createProperty("id", OType.INTEGER);
            car.createProperty("mark", OType.STRING);
            car.createProperty("model", OType.STRING);
            car.createProperty("year", OType.INTEGER);
        }

        OClass employee = db.getClass("Employee");
        if (employee == null) {
            employee = db.createVertexClass("Employee");
        }
        if (employee.getProperty("id") == null) {
            employee.createProperty("id", OType.INTEGER);
            employee.createProperty("firstname", OType.STRING);
            employee.createProperty("lastname", OType.STRING);
            employee.createProperty("age", OType.INTEGER);
        }

        OClass branch = db.getClass("Branch");
        if (branch == null) {
            branch = db.createVertexClass("Branch");
        }
        if (branch.getProperty("id") == null) {
            branch.createProperty("id", OType.INTEGER);
            branch.createProperty("car_id", OType.INTEGER);
            branch.createProperty("employee_id", OType.INTEGER);
            branch.createProperty("City", OType.STRING);
        }
    }
}


