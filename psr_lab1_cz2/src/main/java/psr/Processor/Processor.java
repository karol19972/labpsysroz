package psr.Processor;

import com.hazelcast.aggregation.Aggregators;
import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IExecutorService;
import com.hazelcast.map.IMap;
import psr.HConfig;
import psr.models.Client;
import psr.models.Film;
import psr.models.Transaction;

import java.net.UnknownHostException;
import java.util.Date;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;

public class Processor {
    final private static Random r = new Random(System.currentTimeMillis());
    private IMap<Long, Client> clients;
    private IMap<Long, Transaction> transactions;
    public void init() throws UnknownHostException {

        //
        String operation;
        do{
            System.out.println("[1] Po stronie klienta\n[2] Po stronie serwera\n");
            Scanner scan = new Scanner(System.in);
            operation = scan.nextLine();
            switch (operation){
                case "1":
                    HExecutorService hExecutorService = new HExecutorService();
                    hExecutorService.init();
                    break;
                case "2":
                    ClientConfig clientConfig = HConfig.getClientConfig();
                    final HazelcastInstance instance = HazelcastClient.newHazelcastClient(clientConfig);
                    clients = instance.getMap("clients");
                    transactions = instance.getMap("transacitons");
                    IExecutorService executorService = instance.getExecutorService("exec");
                    executorService.submitToAllMembers(new HCallable());
                    break;
            }
        }while (!(operation.equals("1")||operation.equals("2")));

        //





    }
}
