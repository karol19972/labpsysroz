package psr.Processor;

import com.hazelcast.aggregation.Aggregators;
import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceAware;
import com.hazelcast.core.IExecutorService;
import com.hazelcast.map.IMap;
import psr.models.Client;
import psr.models.Transaction;
import psrTestingFromLecture.HConfig;
import psrTestingFromLecture.Student;

import java.io.Serializable;
import java.net.UnknownHostException;
import java.util.Set;
import java.util.concurrent.Callable;

public class HExecutorService {

    public void init() throws UnknownHostException {
        ClientConfig clientConfig = HConfig.getClientConfig();
        final HazelcastInstance client = HazelcastClient.newHazelcastClient( clientConfig );

		IExecutorService executorService = client.getExecutorService("exec");
		executorService.submitToAllMembers(new HCallable());
	}
}

class HCallable implements Callable<Integer>, Serializable, HazelcastInstanceAware {
	private static final long serialVersionUID = 1L;	
	private transient HazelcastInstance instance;
	
	@Override
	public Integer call() {

		IMap<Long, Client> clients = instance.getMap("clients");
		IMap<Long, Transaction> transactions = instance.getMap("transacitons");

		Set<Long> keys = clients.localKeySet();
		Set<Long> keys2 = transactions.localKeySet();
		System.out.println("Server");
		System.out.println("Srednia wieku klientow wynosi: " +
				clients.aggregate(Aggregators.integerAvg("age")) + " lat");
		System.out.println("Suma przychodow z transakcji wynosi: " +
				transactions.aggregate(Aggregators.doubleSum("price")));
		return keys.size() + keys2.size();
	}

	@Override
	public void setHazelcastInstance(HazelcastInstance instance) {
		this.instance = instance;
	}
}