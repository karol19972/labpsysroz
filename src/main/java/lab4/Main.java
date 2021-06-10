package lab4;

import java.util.Scanner;

import org.neo4j.ogm.config.Configuration;
import org.neo4j.ogm.session.Session;
import org.neo4j.ogm.session.SessionFactory;

public class Main {
    public static void main(String[] args) {
        Configuration configuration = new Configuration.Builder().uri("bolt://localhost:7687").credentials("neo4j", "password").build();
        SessionFactory sessionFactory = new SessionFactory(configuration,"lab4");
        Session session = sessionFactory.openSession();
        session.purgeDatabase();
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


//        BookService bookService = new BookService(session);
//
//        Author author1 = new Author("Sienkiewicz");
//        Book book1 = new Book("Potop");
//        book1.addAuthor(author1);
//        bookService.createOrUpdate(book1);
//
//        Author author2 = new Author("Mickiewicz");
//        Author author3 = new Author("Słowacki");
//        Book book2 = new Book("Wiersze");
//        book2.addAuthor(author2);
//        book2.addAuthor(author3);
//        bookService.createOrUpdate(book2);
//
//        Reader reader1 = new Reader("Kowalski");
//        reader1.addBook(book1);
//        ReaderService readerService = new ReaderService(session);
//        readerService.createOrUpdate(reader1);
//
//        System.out.println("All books:");
//        for(Book b : bookService.readAll())
//            System.out.println(b);
//
//        for(Map<String, Object> map : bookService.getBookRelationships())
//            System.out.println(map);

        sessionFactory.close();
    }
}
