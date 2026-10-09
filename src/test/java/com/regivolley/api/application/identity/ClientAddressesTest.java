package com.regivolley.api.application.identity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** An address in an audit line is truncated: the network of an attack, not a household (threat model section 7). */
class ClientAddressesTest {

    @ParameterizedTest
    @CsvSource({
            "203.0.113.77, 203.0.113.0/24",
            "10.1.2.3, 10.1.2.0/24",
            "2001:db8:abcd:12:1:2:3:4, 2001:db8:abcd::/48",
            "::ffff:198.51.100.9, 198.51.100.0/24",
            "fe80:1:2:3::1%eth0, fe80:1:2::/48"})
    void truncatesIpv4ToItsSlash24AndIpv6ToItsSlash48(String address, String expected) {
        // Arrange
        // (the parameters)

        // Act
        String truncated = ClientAddresses.truncate(address);

        // Assert
        assertThat(truncated).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "not-an-address", "1.2.3", "::1"})
    void anythingElseIsReportedAsUnknownNeverEchoed(String address) {
        // Arrange
        // (the parameter)

        // Act
        String truncated = ClientAddresses.truncate(address);

        // Assert
        assertThat(truncated).isEqualTo("unknown");
    }

    @Test
    void neverReturnsTheFullAddress() {
        // Arrange
        String address = "198.51.100.200";

        // Act
        String truncated = ClientAddresses.truncate(address);

        // Assert
        assertThat(truncated).doesNotContain("200");
    }
}
