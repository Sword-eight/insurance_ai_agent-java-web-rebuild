package com.insurance.platform.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.insurance.platform.client.dto.AgentChatRequest;
import com.insurance.platform.client.dto.HistoryMessage;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClientContractTests {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void clientsRemainPortsWithoutConcreteHttpImplementation() {
        assertThat(AgentClient.class).isInterface();
        assertThat(KnowledgeClient.class).isInterface();
    }

    @Test
    void validChatRequestMatchesFrozenContract() {
        AgentChatRequest request = new AgentChatRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                " 等待期一般有多久？ ",
                List.of(
                        new HistoryMessage("user", "我想了解医疗险"),
                        new HistoryMessage("assistant", "你想了解哪一方面？")));

        assertThat(validator.validate(request)).isEmpty();
        assertThat(request.message()).isEqualTo("等待期一般有多久？");
    }

    @Test
    void invalidChatRequestRejectsIncompleteHistoryPair() {
        AgentChatRequest request = new AgentChatRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "问题",
                List.of(new HistoryMessage("user", "孤立消息")));

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .contains("history must contain complete alternating user/assistant pairs");
    }

    @Test
    void invalidChatRequestRejectsHistoryOverCharacterBudget() {
        String longContent = "x".repeat(3001);
        AgentChatRequest request = new AgentChatRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "问题",
                List.of(
                        new HistoryMessage("user", longContent),
                        new HistoryMessage("assistant", longContent),
                        new HistoryMessage("user", longContent),
                        new HistoryMessage("assistant", longContent)));

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .contains("history content must not exceed 12000 characters");
    }
}
