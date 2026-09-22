package com.indira.opsconsole;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

import java.io.File;

@SpringBootApplication
@EnableAsync
public class OpsConsoleApplication {

    private static final Logger log = LoggerFactory.getLogger(OpsConsoleApplication.class);

    public static void main(String[] args) {
        // Ensure the default data directory exists before Spring initialises the datasource.
        // The DB_PATH env-var can override this; here we handle the default "./data/" case.
        String dbPath = System.getenv("DB_PATH");
        if (dbPath == null) {
            dbPath = System.getProperty("DB_PATH", "./data/ops_console.db");
        }
        File dbFile = new File(dbPath);
        File dbDir  = dbFile.getParentFile();
        if (dbDir != null && !dbDir.exists()) {
            boolean created = dbDir.mkdirs();
            if (created) {
                log.info("Created database directory: {}", dbDir.getAbsolutePath());
            }
        }

        SpringApplication.run(OpsConsoleApplication.class, args);
    }
}
