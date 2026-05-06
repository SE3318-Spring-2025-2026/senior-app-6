package com.senior.spm.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.senior.spm.controller.request.StudentUploadRequest;
import com.senior.spm.controller.response.StudentSearchResponse;
import com.senior.spm.entity.Student;
import com.senior.spm.exception.AlreadyExistsException;
import com.senior.spm.exception.NotFoundException;
import com.senior.spm.repository.StudentRepository;

@Service
public class StudentService {

    private final StudentRepository studentRepository;

    public StudentService(StudentRepository studentRepository) {
        this.studentRepository = studentRepository;
    }

    @Transactional
    public void uploadStudentData(StudentUploadRequest request) {
        if (request.getStudentIds().size() != request.getStudentIds().stream().distinct().count()) {
            throw new IllegalArgumentException("Duplicate student IDs found in the request");
        }

        for (String studentId : request.getStudentIds()) {
            if (studentRepository.existsByStudentId(studentId)) {
                throw new AlreadyExistsException("Id " + studentId + " already exists in the database");
            }
        }

        var students = request.getStudentIds().stream().map(id -> {
            Student student = new Student();
            student.setStudentId(id);
            return student;
        }).toList();

        if (students.isEmpty()) {
            throw new IllegalArgumentException("No valid student IDs provided");
        }

        studentRepository.saveAll(students);
    }

    @Transactional(readOnly = true)
    public Student getStudentByStudentId(String studentId) {
        return studentRepository.findByStudentId(studentId)
                .orElseThrow(() -> new NotFoundException("Student not found"));
    }

    @Transactional(readOnly = true)
    public List<StudentSearchResponse> searchAvailableStudents(String q) {
        if (q == null || q.trim().length() < 3) {
            throw new IllegalArgumentException("Query must be at least 3 characters");
        }
        return studentRepository.findUngroupedByStudentIdContaining(q.trim())
            .stream()
            .map(s -> new StudentSearchResponse(s.getStudentId(), s.getGithubUsername()))
            .toList();
    }
}
