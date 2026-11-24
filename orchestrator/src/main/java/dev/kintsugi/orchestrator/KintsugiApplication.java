package dev.kintsugi.orchestrator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import java.nio.file.Path;

@SpringBootApplication
public class KintsugiApplication {
    @Bean
    AssessmentCli assessmentCli() {
        return new AssessmentCli(Path.of("/proc"), System.getProperty("os.name").equals("Linux"));
    }

    public static void main(String[] args) {
        int exit;
        // CLI arguments belong to the command parser, including unknown options.
        try (var context = SpringApplication.run(KintsugiApplication.class)) {
            exit = context.getBean(AssessmentCli.class).run(args, System.out);
        }
        System.exit(exit);
    }
}
