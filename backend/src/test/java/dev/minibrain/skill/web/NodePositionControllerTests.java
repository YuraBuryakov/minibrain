package dev.minibrain.skill.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:sqlite::memory:")
@AutoConfigureMockMvc
class NodePositionControllerTests {

    @Autowired
    MockMvc mvc;

    @Test
    void pinMoveAndReset() throws Exception {
        mvc.perform(put("/api/layout/positions/area:ddd").contentType(MediaType.APPLICATION_JSON).content("{\"x\": 10.5, \"y\": -3}"))
                .andExpect(status().isNoContent());
        mvc.perform(put("/api/layout/positions/area:ddd").contentType(MediaType.APPLICATION_JSON).content("{\"x\": 20, \"y\": 30}"))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/layout/positions"))
                .andExpect(jsonPath("$[?(@.id == 'area:ddd')].x").value(20.0))
                .andExpect(jsonPath("$[?(@.id == 'area:ddd')].y").value(30.0));

        mvc.perform(delete("/api/layout/positions")).andExpect(status().isNoContent());
        mvc.perform(get("/api/layout/positions")).andExpect(jsonPath("$.length()").value(0));
    }
}
