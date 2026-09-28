package dev.minibrain;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.datasource.url=jdbc:sqlite::memory:")
class MinibrainApplicationTests {

    @Test
    void contextLoads() {
    }
}
