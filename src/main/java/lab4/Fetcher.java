package lab4;

import lab4.model.*;
import org.neo4j.ogm.session.Session;

import java.util.ArrayList;
import java.util.Scanner;

public class Fetcher {
    private BranchService branchService;
    private CarService carService;
    private EmployeeService employeeService;
    private Session session;

    public Fetcher(Session session) {
        this.session = session;
        this.branchService = new BranchService(session);
        this.carService = new CarService(session);
        this.employeeService = new EmployeeService(session);
    }


    public void getValues() {
        String operation;
        do{
            System.out.println("[1] Wyswietl wszystko\n[2] Wyszukaj po id\n[3] Wyszukaj po innych parametrach\n");
            Scanner scan = new Scanner(System.in);
            operation = scan.nextLine();
            switch (operation){
                case "1":
                    getAllCars();
                    getAllBranches();
                    getAllEmployees();
                    break;
                case "2":
                    System.out.println("Wyszukam samochod z id 2");
                    searchAfterId();
                    break;
                case "3":
                    System.out.println("Wyszukam pracownikow starszych od 40-latkow");
                    searchAfterOtherParameters();
                    break;
            }
        }while (!(operation.equals("1")||operation.equals("2")||operation.equals("3")));
    }


    private void searchAfterOtherParameters() {
        ArrayList<Employee> employeesAfterAge = new ArrayList<>();
        for(Employee employee : employeeService.readAll()){
            if(employee.getAge()>40) employeesAfterAge.add(employee);
        }
        System.out.println(employeesAfterAge);

    }

    private void searchAfterId() {
        Car car = carService.read(2L);
        System.out.println(car);
    }


    private void getAllEmployees() {
        for(Employee employee : employeeService.readAll())
            System.out.println(employee);
    }

    private void getAllBranches() {
        for(Branch branch : branchService.readAll())
            System.out.println(branch);
    }

    private void getAllCars() {
        for(Car car : carService.readAll())
            System.out.println(car);
    }
}
