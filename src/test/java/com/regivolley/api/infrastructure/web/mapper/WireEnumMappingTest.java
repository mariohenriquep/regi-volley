package com.regivolley.api.infrastructure.web.mapper;

import com.regivolley.api.infrastructure.web.dto.AttendanceMarkName;
import com.regivolley.api.infrastructure.web.dto.MemberRoleName;
import com.regivolley.api.infrastructure.web.dto.PaymentMethodName;
import com.regivolley.api.infrastructure.web.dto.PaymentStatusName;
import com.regivolley.api.infrastructure.web.dto.PlanTypeName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Each enum a client can name has its own wire spelling and an exhaustive {@code switch} to the domain's type, so a value added on
 * either side is a compile error. This test pins the other half: the spellings agree, so a client's {@code PACK} is the domain's {@code PACK}.
 */
class WireEnumMappingTest {

    @Test
    void everyWireSpellingMapsToTheDomainValueOfTheSameName() {
        // Arrange
        // (the enums)

        // Act + Assert
        for (MemberRoleName name : MemberRoleName.values()) {
            assertThat(MemberWebMapper.toDomain(name).name()).isEqualTo(name.name());
        }
        for (PaymentStatusName name : PaymentStatusName.values()) {
            assertThat(SubscriptionWebMapper.toDomain(name).name()).isEqualTo(name.name());
        }
        for (PaymentMethodName name : PaymentMethodName.values()) {
            assertThat(SubscriptionWebMapper.toDomain(name).name()).isEqualTo(name.name());
        }
        for (PlanTypeName name : PlanTypeName.values()) {
            assertThat(PlanWebMapper.type(name).name()).isEqualTo(name.name());
        }
        for (AttendanceMarkName name : AttendanceMarkName.values()) {
            assertThat(SessionWebMapper.toDomain(name).name()).isEqualTo(name.name());
        }
    }

    @Test
    void theWireEnumsHaveExactlyTheValuesOfTheDomainOnes() {
        // Arrange
        // (the enums)

        // Act + Assert - the domain side cannot grow a value the wire does not know either
        assertThat(com.regivolley.api.domain.model.valueobject.MemberRole.values()).hasSameSizeAs(MemberRoleName.values());
        assertThat(com.regivolley.api.domain.model.valueobject.PaymentStatus.values()).hasSameSizeAs(PaymentStatusName.values());
        assertThat(com.regivolley.api.domain.model.valueobject.PaymentMethod.values()).hasSameSizeAs(PaymentMethodName.values());
        assertThat(com.regivolley.api.domain.model.valueobject.PlanType.values()).hasSameSizeAs(PlanTypeName.values());
        assertThat(com.regivolley.api.application.command.AttendanceMark.values()).hasSameSizeAs(AttendanceMarkName.values());
    }
}
