package com.bet99.report;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import com.bet99.report.repository.AccountTranRepository;

@SpringBootApplication
public class TransactionReportApplication {

    public static void main(String[] args) {
        SpringApplication.run(TransactionReportApplication.class, args);
    }

    // TEMPORARY - proves the entity mapping works. Delete after checking.
    @Bean
    CommandLineRunner probe(AccountTranRepository repo) {
        return args -> {
            System.out.println(">>> total rows    : " + repo.count());
            System.out.println(">>> tran types    : " + repo.findDistinctTranTypes());
            Object[] window = repo.findDataWindow().get(0);
            System.out.println(">>> oldest        : " + window[0]);
            System.out.println(">>> newest        : " + window[1]);
        };
    }
}