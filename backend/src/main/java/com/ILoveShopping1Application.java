package com;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.TimeZone;

@EnableAsync
@SpringBootApplication
public class ILoveShopping1Application {

    public static void main(String[] args) {

        // ensure the JVM default timezone is UTC so date/time handling is consistent
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        Dotenv dotenv = Dotenv.configure()
                .ignoreIfMissing()
                .load();

        dotenv.entries().forEach(e ->
                System.setProperty(e.getKey(), e.getValue())
        );

        SpringApplication.run(ILoveShopping1Application.class, args);
    }

}
