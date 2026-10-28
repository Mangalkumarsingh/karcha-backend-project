package com.example.demo;

import com.example.demo.model.AppUser;
import com.example.demo.model.ExpenseGroup;
import com.example.demo.model.GroupMember;
import com.example.demo.model.GroupRole;
import com.example.demo.model.MemberStatus;
import com.example.demo.repository.ExpenseGroupRepository;
import com.example.demo.repository.GroupMemberRepository;
import com.example.demo.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class GroupControllerMockMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ExpenseGroupRepository groupRepository;

    @Autowired
    private GroupMemberRepository groupMemberRepository;

    private AppUser userA;
    private AppUser userB;

    @BeforeEach
    void setUp() {
        groupMemberRepository.deleteAll();
        groupRepository.deleteAll();
        userRepository.deleteAll();

        userA = userRepository.save(AppUser.builder()
                .name("Alice")
                .phoneNumber("+919876543210")
                .email("alice@example.com")
                .phoneVerified(true)
                .build());

        userB = userRepository.save(AppUser.builder()
                .name("Bob")
                .phoneNumber("+919876543211")
                .email("bob@example.com")
                .phoneVerified(true)
                .build());
    }

    @Test
    @DisplayName("POST /api/v1/groups without JWT returns 401 Unauthorized")
    void testCreateGroupWithoutAuthReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Trip Group"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/v1/groups with valid JWT returns 201 Created and group details")
    void testCreateGroupWithAuthReturns201() throws Exception {
        mockMvc.perform(post("/api/v1/groups")
                        .with(jwt().jwt(builder -> builder.subject(userA.getId().toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Manali Trip",
                                  "description": "Mountains holiday",
                                  "currency": "INR"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Manali Trip"))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.memberCount").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/groups/{groupId} by non-member returns 403 Forbidden")
    void testGetGroupDetailsNonMemberReturns403() throws Exception {
        ExpenseGroup group = groupRepository.save(ExpenseGroup.builder()
                .name("Private Group")
                .currency("INR")
                .createdBy(userA)
                .build());

        groupMemberRepository.save(GroupMember.builder()
                .group(group)
                .user(userA)
                .role(GroupRole.ADMIN)
                .status(MemberStatus.ACTIVE)
                .build());

        mockMvc.perform(get("/api/v1/groups/" + group.getId())
                        .with(jwt().jwt(builder -> builder.subject(userB.getId().toString()))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/users/me returns profile info")
    void testGetCurrentUserProfile() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .with(jwt().jwt(builder -> builder.subject(userA.getId().toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userA.getId().toString()))
                .andExpect(jsonPath("$.name").value("Alice"))
                .andExpect(jsonPath("$.phoneNumber").value("+919876543210"));
    }
}
