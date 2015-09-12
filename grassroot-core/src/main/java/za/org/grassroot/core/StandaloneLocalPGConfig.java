package za.org.grassroot.core;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import za.org.grassroot.core.DatabaseConfig;
import za.org.grassroot.core.GrassRootApplicationProfiles;

import javax.sql.DataSource;
import java.util.logging.Logger;

/**
 * @author Lesetse Kimwaga
 */
@Configuration
@Profile(GrassRootApplicationProfiles.LOCAL_PG)
public class StandaloneLocalPGConfig extends DatabaseConfig {

    private Logger log = Logger.getLogger(getClass().getCanonicalName());


    @Autowired
    Environment env;

    private String dbDriver;
    private String dbUrl;
    private String dbUsername;
    private String dbPassword;


    @Override
    public DataSource dataSource() {
        log.info("Running with LOCAL_PG profile");
        //TODO aakil - will this work when we switch to undertow
        //TODO also get the properties to work from an external config file
        org.apache.tomcat.jdbc.pool.DataSource dataSource = new org.apache.tomcat.jdbc.pool.DataSource();

        if (env.getProperty("db.driver") != null && !env.getProperty("db.driver").trim().equals("")) {
            dbDriver = env.getProperty("db.driver");
        } else {
            dbDriver = "org.postgresql.Driver";
        }

        if (env.getProperty("db.url") != null && !env.getProperty("db.url").trim().equals("")) {
            dbUrl = env.getProperty("db.url");
        } else {
            dbUrl = "jdbc:postgresql://localhost:5432/grassroot";
        }

        if (env.getProperty("db.username") != null && !env.getProperty("db.username").trim().equals("")) {
            dbUsername = env.getProperty("db.username");
        } else {
            dbUsername = "grassroot";
        }

        if (env.getProperty("db.password") != null && !env.getProperty("db.password").trim().equals("")) {
            dbPassword = env.getProperty("db.password");
        } else {
            dbPassword = "verylongpassword";
        }

        dataSource.setDriverClassName(dbDriver);
        dataSource.setUrl(dbUrl);
        dataSource.setUsername(dbUsername);
        dataSource.setPassword(dbPassword);
        dataSource.setValidationQuery("SELECT 1");

        configureDataSource(dataSource);
        return dataSource;
    }
}
