package com.example.threadedchatservice.controller

import com.example.threadedchatservice.dto.response.ActivityResponse
import com.example.threadedchatservice.service.admin.AdminService
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
class AdminController(
    private val adminService: AdminService,
) {

    @GetMapping("/activity")
    fun getActivity(): ActivityResponse {
        return adminService.getActivity()
    }

    @GetMapping("/report")
    fun generateReport(): ResponseEntity<ByteArray> {
        val csv = adminService.generateReport()

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=report.csv")
            .contentType(MediaType.parseMediaType("text/csv"))
            .body(csv.toByteArray())
    }
}
