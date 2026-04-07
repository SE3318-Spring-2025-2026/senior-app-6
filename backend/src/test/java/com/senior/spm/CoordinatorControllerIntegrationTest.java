package com.senior.spm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.senior.spm.controller.request.StudentUploadRequest;
import com.senior.spm.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CoordinatorControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanDatabase() {
        studentRepository.deleteAll();
    }

    @Test
    @WithMockUser(roles = "COORDINATOR")
    void shouldUploadStudentsAndSaveCorrectCountToDatabase() throws Exception {
        StudentUploadRequest request = new StudentUploadRequest();
        request.setStudentIds(List.of("12345678901", "12345678902", "12345678903"));

        mockMvc.perform(post("/api/coordinator/students/upload")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isCreated());

        long count = studentRepository.count();
        assertEquals(3, count);
    }

    @Test
    @WithMockUser(roles = "COORDINATOR")
    void shouldRejectStudentUploadWhenIdFormatIsInvalid() throws Exception {
        StudentUploadRequest request = new StudentUploadRequest();
        request.setStudentIds(List.of("1234567890", "12345678902")); // biri 10 haneli

        mockMvc.perform(post("/api/coordinator/students/upload")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "COORDINATOR")
    void shouldRejectStudentUploadWhenDuplicateIdsExist() throws Exception {
        StudentUploadRequest request = new StudentUploadRequest();
        request.setStudentIds(List.of("12345678901", "12345678901", "12345678903"));

        mockMvc.perform(post("/api/coordinator/students/upload")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest());

        long count = studentRepository.count();
        assertEquals(0, count);
    }

    @Test
    @WithMockUser(roles = "COORDINATOR")
    void shouldRejectStudentUploadWhenListIsEmpty() throws Exception {
        StudentUploadRequest request = new StudentUploadRequest();
        request.setStudentIds(List.of());

        mockMvc.perform(post("/api/coordinator/students/upload")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }
}