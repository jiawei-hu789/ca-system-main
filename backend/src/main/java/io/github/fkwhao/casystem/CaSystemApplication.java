package io.github.fkwhao.casystem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CaSystemApplication {

    public static void main(String[] args) throws IOException {
        String dataDirectory = System.getenv().getOrDefault("CA_DATA_DIR", "./data");
        Files.createDirectories(Path.of(dataDirectory));
        SpringApplication.run(CaSystemApplication.class, args);
    }
}
