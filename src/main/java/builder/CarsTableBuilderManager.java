package builder;

import com.datastax.dse.driver.api.core.graph.GraphStatement;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.datastax.oss.driver.api.core.data.UdtValue;
import com.datastax.oss.driver.api.core.type.DataTypes;
import com.datastax.oss.driver.api.querybuilder.QueryBuilder;
import com.datastax.oss.driver.api.querybuilder.SchemaBuilder;
import com.datastax.oss.driver.api.querybuilder.delete.Delete;
import com.datastax.oss.driver.api.querybuilder.insert.Insert;
import com.datastax.oss.driver.api.querybuilder.schema.CreateTable;
import com.datastax.oss.driver.api.querybuilder.schema.CreateType;
import com.datastax.oss.driver.api.querybuilder.schema.Drop;
import com.datastax.oss.driver.api.querybuilder.select.Select;
import com.datastax.oss.driver.api.querybuilder.update.Update;
import menager.SimpleManager;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Scanner;


public class CarsTableBuilderManager extends SimpleManager {

	public CarsTableBuilderManager(CqlSession session) {
		super(session);
	}


	public void insertIntoTable() {
		executeSimpleStatement("INSERT INTO car (id, mark, model, year) " +
				" VALUES (1,'BMW','S3',2020 );");
		executeSimpleStatement("INSERT INTO car (id, mark, model, year) " +
				" VALUES (2,'Audi','A1',2019 );");
		executeSimpleStatement("INSERT INTO transporter (id, firstname, lastname, age) " +
				" VALUES (1,'Marek','Nowak',25 );");
		executeSimpleStatement("INSERT INTO transporter (id, firstname, lastname, age) " +
				" VALUES (2,'Ola','Kowalska',39 );");
		executeSimpleStatement("INSERT INTO transporter (id, firstname, lastname, age) " +
				" VALUES (3,'Tomasz','Olczyk',67 );");
		executeSimpleStatement("INSERT INTO route (id, name, car, transporter) " +
				" VALUES (1,'Wroclaw',1,3 );");
		executeSimpleStatement("INSERT INTO route (id, name, car, transporter) " +
				" VALUES (2,'Olsztyn',2,1 );");
		executeSimpleStatement("INSERT INTO route (id, name, car, transporter) " +
				" VALUES (3,'Warszawa',2,2 );");
	}
	public void process(){
		String statement = "SELECT * FROM transporter;";
		ResultSet resultSet = session.execute(statement);
		int sum = 0;
		int iter = 0;
		for (Row row : resultSet) {
			sum+=row.getInt("age");
			iter++;
		}
		System.out.print((double) sum/iter);

		System.out.println();


	}



	public void updateIntoTable() {
		executeSimpleStatement("UPDATE transporter SET firstname = 'Kasia' WHERE id = 1;");
	}

	public void deleteFromTable() {
		deleteAll("car");
		deleteAll("route");
		deleteAll("transporter");
	}

	private void deleteAll(String name){
		Select query = QueryBuilder.selectFrom(name).all();
		SimpleStatement statement = query.build();
		ResultSet resultSet = session.execute(statement);
		System.out.print(name.toUpperCase()+"s: ");
		for (Row row : resultSet) {
			System.out.print(name+": ");
			executeSimpleStatement("DELETE FROM "+name+" WHERE id = "+row.getInt("id")+";");
			System.out.println();
		}
		System.out.println("Usunieto wszystko");
	}

	public void selectFromTable() {
		String operation;
		do{
			System.out.println("[1] Wyswietl wszystko\n[2] Wyszukaj po id\n[3] Wyszukaj po innych parametrach\n");
			Scanner scan = new Scanner(System.in);
			operation = scan.nextLine();
			switch (operation){
				case "1":
					search("car");
					search("route");
					search("transporter");
					break;
				case "2":
					System.out.println("Wyszukam samochod z id 2");
					showAfterId();
					break;
				case "3":
					System.out.println("Wyszukam transporterow starszych od 30-latkow");
					showAfterOtherParameter();
					break;
			}
		}while (!(operation.equals("1")||operation.equals("2")||operation.equals("3")));
	}

	private void showAfterOtherParameter() {
		String statement = "SELECT * FROM transporter";
		ResultSet resultSet = session.execute(statement);
		ArrayList<BigInteger> ages = new ArrayList<>();
		for (Row row : resultSet) {
			System.out.print("transporter: ");
			System.out.print(row.getInt("id") + ", ");
			System.out.print(row.getString("firstname") + ", ");
			System.out.print(row.getString("lastname") + ", ");
			System.out.print(row.getInt("age"));

			System.out.println();
		}

		System.out.println("Statement \"" + statement + "\" executed successfully");
	}

	private void showAfterId() {
		String statement = "SELECT * FROM car where id=2;";
		ResultSet resultSet = session.execute(statement);
		for (Row row : resultSet) {
			System.out.print("car: ");
			System.out.print(row.getInt("id") + ", ");
			System.out.print(row.getString("mark") + ", ");
			System.out.print(row.getString("model") + ", ");
			System.out.print(row.getInt("year"));
		}
		System.out.println();
		System.out.println("Statement \"" + statement + "\" executed successfully");
	}

	private void search(String name){
		Select query = QueryBuilder.selectFrom(name).all();
		SimpleStatement statement = query.build();
		ResultSet resultSet = session.execute(statement);
		System.out.print(name.toUpperCase()+"s: ");
		for (Row row : resultSet) {
			System.out.print(name+": ");
			if(name.equals("car")) {
				System.out.print(row.getInt("id") + ", ");
				System.out.print(row.getString("mark") + ", ");
				System.out.print(row.getString("model") + ", ");
				System.out.print(row.getInt("year"));
			}
			if(name.equals("route")){
				System.out.print(row.getInt("id") + ", ");
				System.out.print(row.getString("name") + ", ");
				System.out.print(row.getInt("car") + ", ");
				System.out.print(row.getInt("transporter"));
			}
			if(name.equals("transporter")){
				System.out.print(row.getInt("id") + ", ");
				System.out.print(row.getString("firstname") + ", ");
				System.out.print(row.getString("lastname") + ", ");
				System.out.print(row.getInt("age"));
			}
			System.out.println();
		}
		System.out.println("Statement \"" + statement.getQuery() + "\" executed successfully");
	}


}
