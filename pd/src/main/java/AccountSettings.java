import com.azure.cosmos.implementation.apachecommons.lang.StringUtils;

public class AccountSettings {
    // Replace MASTER_KEY and HOST with values from your Azure Cosmos DB account.
    // The default values are credentials of the local emulator, which are not used in any production environment.
    // <!--[SuppressMessage("Microsoft.Security", "CS002:SecretInNextLine")]-->
    public static String MASTER_KEY =
            System.getProperty("ACCOUNT_KEY", 
                    StringUtils.defaultString(StringUtils.trimToNull(
                            System.getenv().get("ACCOUNT_KEY")),
                            "KyXvoHb5x1SyxIoTyInaqWhCOa3DofU10y2CVJQeEoswh23AFAh5cekXNxDBfPAO3Yy7nfgiL3X2gfL1ItGzmQ=="));

    public static String HOST =
            System.getProperty("ACCOUNT_HOST",
                    StringUtils.defaultString(StringUtils.trimToNull(
                            System.getenv().get("ACCOUNT_HOST")),
                            "https://861eda6b-0ee0-4-231-b9ee.documents.azure.com:443/"));
}
