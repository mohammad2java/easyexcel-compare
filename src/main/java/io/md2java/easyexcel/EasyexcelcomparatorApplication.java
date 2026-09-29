package io.md2java.easyexcel;

import io.md2java.easyexcel.config.CompareProperties;
import io.md2java.easyexcel.service.CompareEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@Slf4j
@RequiredArgsConstructor
@EnableConfigurationProperties(CompareProperties.class)
public class EasyexcelcomparatorApplication implements CommandLineRunner {

    private final CompareEngine compareEngine;
    private final CompareProperties compareProperties;


    public static void main(String[] args) {
        SpringApplication.run(EasyexcelcomparatorApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("EasyExcel Comparator Application started successfully.");
        log.info("current working directory: " + System.getProperty("user.dir"));

        compareEngine.compare();

        log.info("EasyExcel Comparator Application finished successfully.");
        log.info(" ");
    }

}
