package io.salad109.conjunctionapi;

import jakarta.annotation.PostConstruct;
import org.orekit.data.DataContext;
import org.orekit.data.DataProvidersManager;
import org.orekit.data.DirectoryCrawler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.File;

import static org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO;

@SpringBootApplication
@EnableScheduling
@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)
public class ConjunctionApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConjunctionApiApplication.class, args);
    }

    @PostConstruct
    public void initOrekit() {
        DataProvidersManager manager = DataContext.getDefault().getDataProvidersManager();

        // Try Docker path first, fall back to local development path
        File dockerPath = new File("/app/orekit-data");
        File localPath = new File("src/main/resources/orekit-data");

        if (dockerPath.exists()) {
            manager.addProvider(new DirectoryCrawler(dockerPath));
        } else {
            manager.addProvider(new DirectoryCrawler(localPath));
        }
    }

}
