package dev.kintsugi.orchestrator;

import dev.kintsugi.assurance.Assessment;
import dev.kintsugi.linux.JvmInventory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

@SpringBootApplication
@EnableConfigurationProperties(KintsugiApplication.InventoryProperties.class)
public class KintsugiApplication {
    public static void main(String[] args) {
        try (var context = SpringApplication.run(KintsugiApplication.class, args)) {
            // This command owns no long-lived resources.
        }
    }

    @Bean
    ApplicationRunner inventoryCommand(InventoryProperties properties) {
        return args -> {
            List<String> commands = args.getNonOptionArgs();
            if (commands.isEmpty() || args.containsOption("help")) {
                System.out.println("Kintsugi command: inventory (Linux, read-only)");
                return;
            }
            if (!commands.equals(List.of("inventory"))) {
                throw new IllegalArgumentException("Expected the inventory command");
            }
            if (!System.getProperty("os.name").equalsIgnoreCase("Linux")) {
                throw new IllegalStateException("Inventory requires Linux procfs");
            }

            var snapshot = new JvmInventory(Path.of("/proc")).scan(properties.budget());
            System.out.printf("scope=visible-java-launchers boot_id=%s unreadable=%d truncated=%s%n",
                    snapshot.bootId(), snapshot.unreadable(), snapshot.truncated());
            var conclusion = Assessment.assess(Assessment.HostEvidence.UNVERIFIED,
                    Assessment.Behavior.INCONCLUSIVE);
            for (var process : snapshot.candidates()) {
                System.out.printf("pid=%d start_ticks=%d coverage=%s%n",
                        process.pid(), process.startTicks(), conclusion);
            }
        };
    }

    @ConfigurationProperties("kintsugi.inventory")
    record InventoryProperties(Duration budget) {
    }
}
