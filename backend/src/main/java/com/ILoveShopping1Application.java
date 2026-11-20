package com;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;

import java.time.Clock;
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

    // "What time is it?" as an injectable dependency (@SpringBootApplication is also a @Configuration).
    // TOTP codes depend on the time, so tests swap this for a fixed clock - otherwise a test could
    // fail at random whenever it runs across a 30-second boundary.
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
