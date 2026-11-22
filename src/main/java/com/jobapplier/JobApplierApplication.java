package com.jobapplier;

import com.jobapplier.config.AppProperties;
import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(AppProperties.class)
public class JobApplierApplication {

    static {
        // The PostgreSQL JDBC driver sends the JVM default timezone as the session "TimeZone"
        // startup parameter. Windows maps India to the legacy id "Asia/Calcutta", which the
        // server's tzdata rejects before the connection is established:
        //   FATAL: invalid value for parameter "TimeZone": "Asia/Calcutta"
        // Pin the JVM to UTC so every connection (app, tests, IDE runs) starts cleanly.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    public static void main(String[] args) {
        SpringApplication.run(JobApplierApplication.class, args);
    }
}
