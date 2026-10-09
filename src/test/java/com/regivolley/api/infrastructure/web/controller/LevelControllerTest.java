package com.regivolley.api.infrastructure.web.controller;

import com.regivolley.api.application.command.AddLevelCommand;
import com.regivolley.api.application.command.ChangeEntryLevelCommand;
import com.regivolley.api.application.command.RenameLevelCommand;
import com.regivolley.api.application.command.ReorderLevelsCommand;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.valueobject.LevelId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpMethod;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** US-03 over HTTP: every level operation answers with the association's levels, lowest first, and the entry level. */
class LevelControllerTest extends AbstractControllerWebTest {

    private Association other;

    @BeforeEach
    void setUpAssociation() {
        other = WebFixtures.association();
    }

    @Test
    void addingALevelAnswers201WithTheLevelsInRankOrder() throws Exception {
        // Arrange
        when(addLevelUseCase.execute(any())).thenReturn(other.addLevel("Advanced"));

        // Act
        var result = authenticated(HttpMethod.POST, "/api/v1/levels", "{\"name\":\"Advanced\"}");

        // Assert
        result.andExpect(status().isCreated())
                .andExpect(jsonPath("$.levels[0].name").value("Beginner"))
                .andExpect(jsonPath("$.levels[2].name").value("Advanced"))
                .andExpect(jsonPath("$.levels[2].rank").value(2))
                .andExpect(jsonPath("$.entryLevelId").value(other.entryLevelId().value().toString()));
        ArgumentCaptor<AddLevelCommand> command = ArgumentCaptor.forClass(AddLevelCommand.class);
        verify(addLevelUseCase).execute(command.capture());
        assertThat(command.getValue().name()).isEqualTo("Advanced");
        assertThat(command.getValue().actor()).isEqualTo(expectedActor());
    }

    @Test
    void aBlankOrOversizedNameIs400() throws Exception {
        // Arrange
        String blank = "{\"name\":\" \"}";
        String huge = "{\"name\":\"" + "n".repeat(51) + "\"}";

        // Act
        var first = authenticated(HttpMethod.POST, "/api/v1/levels", blank);
        var second = authenticated(HttpMethod.POST, "/api/v1/levels", huge);

        // Assert
        first.andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields[0]").value("name"));
        second.andExpect(status().isBadRequest());
        verifyNoInteractions(addLevelUseCase);
    }

    @Test
    void renamingPassesTheLevelFromThePathAndTheNewName() throws Exception {
        // Arrange
        LevelId level = other.entryLevelId();
        when(renameLevelUseCase.execute(any())).thenReturn(other.renameLevel(level, "Novice"));

        // Act
        var result = authenticated(HttpMethod.PUT, "/api/v1/levels/" + level, "{\"name\":\"Novice\"}");

        // Assert
        result.andExpect(status().isOk()).andExpect(jsonPath("$.levels[0].name").value("Novice"));
        ArgumentCaptor<RenameLevelCommand> command = ArgumentCaptor.forClass(RenameLevelCommand.class);
        verify(renameLevelUseCase).execute(command.capture());
        assertThat(command.getValue().levelId()).isEqualTo(level);
        assertThat(command.getValue().newName()).isEqualTo("Novice");
    }

    @Test
    void reorderingPassesTheIdsInTheGivenOrder() throws Exception {
        // Arrange
        LevelId first = other.levels().get(0).id();
        LevelId second = other.levels().get(1).id();
        when(reorderLevelsUseCase.execute(any())).thenReturn(other.reorderLevels(java.util.List.of(second, first)));

        // Act
        var result = authenticated(HttpMethod.PUT, "/api/v1/levels/order", "{\"levelIds\":[\"" + second + "\",\"" + first + "\"]}");

        // Assert
        result.andExpect(status().isOk()).andExpect(jsonPath("$.levels[0].name").value("Intermediate"));
        ArgumentCaptor<ReorderLevelsCommand> command = ArgumentCaptor.forClass(ReorderLevelsCommand.class);
        verify(reorderLevelsUseCase).execute(command.capture());
        assertThat(command.getValue().orderedLevelIds()).containsExactly(second, first);
    }

    @Test
    void reorderingWithNoIdsOrABadIdIs400() throws Exception {
        // Arrange
        // (the bodies)

        // Act
        var empty = authenticated(HttpMethod.PUT, "/api/v1/levels/order", "{\"levelIds\":[]}");
        var bad = authenticated(HttpMethod.PUT, "/api/v1/levels/order", "{\"levelIds\":[\"x\"]}");

        // Assert
        empty.andExpect(status().isBadRequest());
        bad.andExpect(status().isBadRequest());
        verifyNoInteractions(reorderLevelsUseCase);
    }

    @Test
    void changingTheEntryLevelPassesTheLevel() throws Exception {
        // Arrange
        LevelId second = other.levels().get(1).id();
        when(changeEntryLevelUseCase.execute(any())).thenReturn(other.changeEntryLevel(second));

        // Act
        var result = authenticated(HttpMethod.PUT, "/api/v1/levels/entry-level", "{\"levelId\":\"" + second + "\"}");

        // Assert
        result.andExpect(status().isOk()).andExpect(jsonPath("$.entryLevelId").value(second.value().toString()));
        ArgumentCaptor<ChangeEntryLevelCommand> command = ArgumentCaptor.forClass(ChangeEntryLevelCommand.class);
        verify(changeEntryLevelUseCase).execute(command.capture());
        assertThat(command.getValue().levelId()).isEqualTo(second);
    }

    @Test
    void theEntryLevelNeedsALevelId() throws Exception {
        // Arrange
        String body = "{\"levelId\":null}";

        // Act
        var result = authenticated(HttpMethod.PUT, "/api/v1/levels/entry-level", body);

        // Assert
        result.andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields[0]").value("levelId"));
        assertThat(UUID.randomUUID()).isNotNull();
        verifyNoInteractions(changeEntryLevelUseCase);
    }
}
