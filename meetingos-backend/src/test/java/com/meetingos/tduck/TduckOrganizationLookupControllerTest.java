package com.meetingos.tduck;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TduckOrganizationLookupControllerTest {
    @Autowired private MockMvc mvc;
    @MockBean private TduckOrganizationLookupService service;

    @Test
    void anonymousFormCanRequestTokenAndSuggestionsWithoutLogin() throws Exception {
        when(service.issueToken("form")).thenReturn(new TduckOrganizationLookupService.LookupConfig(true, "scoped-token", 600));
        mvc.perform(get("/api/public/tduck/organization-lookup-token").param("formKey", "form"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.token").value("scoped-token"));
        when(service.search(eq("form"), eq("scoped-token"), eq("北京大学"), anyString()))
                .thenReturn(TianyanchaClient.Suggestions.ok(List.of(new TianyanchaClient.OrganizationSuggestion("1", "北京大学", "北京", "存续"))));
        mvc.perform(post("/api/public/tduck/organization-suggestions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"formKey\":\"form\",\"token\":\"scoped-token\",\"word\":\"北京大学\"}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.items[0].name").value("北京大学"));
    }

    @Test
    void missingTokenIsRejectedBeforeSearchService() throws Exception {
        mvc.perform(post("/api/public/tduck/organization-suggestions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"formKey\":\"form\",\"word\":\"北京大学\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
