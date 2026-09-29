package io.md2java.easyexcel;

import io.md2java.easyexcel.service.CompareEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@Slf4j
@RequiredArgsConstructor
public class EasyexcelcomparatorApplication implements CommandLineRunner {

    private final CompareEngine compareEngine;


    public static void main(String[] args) {
        SpringApplication.run(EasyexcelcomparatorApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("EasyExcel Comparator Application started successfully.");
        log.info("current working directory: " + System.getProperty("user.dir"));


        log.info("EasyExcel Comparator Application finished successfully.");
        log.info(" ");
    }

}
