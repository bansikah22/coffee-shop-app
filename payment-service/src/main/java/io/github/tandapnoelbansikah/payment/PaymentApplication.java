package io.github.tandapnoelbansikah.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Starts either the standalone sandbox payment API or the isolated demo consumer. */
@SpringBootApplication
@EnableScheduling
public class PaymentApplication {
    public static void main(String[] arguments) {
        SpringApplication.run(PaymentApplication.class, arguments);
    }
}