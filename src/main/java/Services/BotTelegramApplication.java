package Services;

import java.util.Properties;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.PropertySource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@PropertySource("classpath:application.properties") // <-- ¡ESTA ES LA LÍNEA VITAL!
public class BotTelegramApplication {
    public static void main(String[] args) {
        SpringApplication.run(BotTelegramApplication.class, args);
    }
}