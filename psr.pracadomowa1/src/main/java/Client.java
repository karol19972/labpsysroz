/*-
 * Copyright (C) 2011, 2020 Oracle and/or its affiliates. All rights reserved.
 *
 * This file was distributed by Oracle as part of a version of Oracle NoSQL
 * Database made available at:
 *
 * http://www.oracle.com/technetwork/database/database-technologies/nosqldb/downloads/index.html
 *
 * Please see the LICENSE file included in the top-level directory of the
 * appropriate version of Oracle NoSQL Database for a copy of the license and
 * additional information.
 */


import models.*;
import oracle.kv.FaultException;
import oracle.kv.KVStore;
import oracle.kv.KVStoreConfig;
import oracle.kv.KVStoreFactory;

import java.util.ArrayList;
import java.util.Scanner;

public class Client {

    private final KVStore store;
    ArrayList<models.Client> clients = new ArrayList<>();
    ArrayList<Transaction> transactions = new ArrayList<>();
    ArrayList<Film> films = new ArrayList<>();
    ArrayList<String> clientFieldsName = new ArrayList<>();
    ArrayList<String> transactionFieldsName = new ArrayList<>();
    ArrayList<String> filmFieldsName = new ArrayList<>();


    public static void main(String args[]) throws InterruptedException {

        try {
            Client example = new Client(args);
            example.runExample();

        } catch (FaultException e) {
            e.printStackTrace();
            System.out.println("Please make sure a store is running.");
            System.out.println("The error could be caused by a security " +
                    "mismatch. If the store is configured secure, you " +
                    "should specify a user login file " +
                    "with system property oracle.kv.security. " +
                    "For example,\n" +
                    "\tjava -Doracle.kv.security=<user security login " +
                    "file> hello.HelloBigDataWorld\n" +
                    "KVLite generates the security file in " +
                    "$KVHOME/kvroot/security/user.security\n" +
                    "The error could also be caused by client using the " +
                    "async network protocol with a server that does not " +
                    "support that protocol. To disable async network " +
                    "protocol, set system property oracle.kv.async to "+
                    "false.");
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
    }

    private void initFields(){
        this.clientFieldsName.add("name");
        this.clientFieldsName.add("surname");
        this.clientFieldsName.add("age");
        this.clientFieldsName.add("PESEL");
        this.filmFieldsName.add("name");
        this.filmFieldsName.add("fromAge");
        this.transactionFieldsName.add("price");
        this.transactionFieldsName.add("filmName");
        this.transactionFieldsName.add("clientNumber");
    }

    Client(String[] argv) {
        initFields();
        String storeName = "kvstore";
        String hostName = "DESKTOP-P7OHHRP";
        String hostPort = "5000";
        store = KVStoreFactory.getStore
                (new KVStoreConfig(storeName, hostName + ":" + hostPort));
    }

    private void usage(String message) {
        System.out.println("\n" + message + "\n");
        System.out.println("usage: " + getClass().getName());
        System.out.println("\t-store <instance name> (default: kvstore) " +
                "-host <host name> (default: localhost) " +
                "-port <port number> (default: 5000)");
        System.exit(1);
    }


    void runExample() {
        System.out.println("[0] quit\n[1] save\n[2] update\n[3] delete\n[4] fetch\n[5] process\n");
        Scanner scan = new Scanner(System.in);
        String operation = scan.nextLine();
        do{
            switch (operation){
                case "0":
                    System.out.println("Program zakończono pomyślnie");
                    break;
                case "1":
                    Saver saver = new Saver(store,clients,films,transactions,clientFieldsName,transactionFieldsName,
                            filmFieldsName);
                    saver.setInitialValues();
                    break;
                case "2":
                    saver = new Saver(store,clients,films,transactions,clientFieldsName,transactionFieldsName,
                            filmFieldsName);
                    saver.fillTables();
                    Updater updater = new Updater(store,films,clients,transactions,clientFieldsName,transactionFieldsName,
                            filmFieldsName);
                    updater.changeName();
                    break;
                case "3":
                    saver = new Saver(store,clients,films,transactions,clientFieldsName,transactionFieldsName,
                            filmFieldsName);
                    saver.fillTables();
                    Cleaner cleaner = new Cleaner(store,films,clients,transactions,clientFieldsName,transactionFieldsName,
                            filmFieldsName);
                    cleaner.clearAll();
                    break;
                case "4":
                    saver = new Saver(store,clients,films,transactions,clientFieldsName,transactionFieldsName,
                            filmFieldsName);
                    saver.fillTables();
                    Searcher searcher = new Searcher(store,films,clients,transactions,clientFieldsName,transactionFieldsName,
                            filmFieldsName);
                    searcher.getValues();
                    break;
                case "5":
                    saver = new Saver(store,clients,films,transactions,clientFieldsName,transactionFieldsName,
                            filmFieldsName);
                    saver.fillTables();
                    Processor processor = new Processor(store,films,clients,transactions,clientFieldsName,transactionFieldsName,
                            filmFieldsName);
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
