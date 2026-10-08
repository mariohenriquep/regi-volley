package com.regivolley.api.application.identity;

/**
 * An IP address is personal data (threat model section 7): audit lines carry it truncated - IPv4 to its /24, IPv6 to its /48 -
 * which still points at the network of an attack without naming a household.
 */
public final class ClientAddresses {

    private ClientAddresses() {
    }

    public static String truncate(String address) {
        if (address == null || address.isBlank()) {
            return "unknown";
        }
        if (address.indexOf(':') >= 0) {
            return truncateIpv6(address);
        }
        String[] parts = address.split("\\.");
        if (parts.length != 4) {
            return "unknown";
        }
        return parts[0] + "." + parts[1] + "." + parts[2] + ".0/24";
    }

    private static String truncateIpv6(String address) {
        String withoutZone = address.contains("%") ? address.substring(0, address.indexOf('%')) : address;
        if (withoutZone.startsWith("::ffff:") && withoutZone.indexOf('.') >= 0) {
            return truncate(withoutZone.substring("::ffff:".length()));
        }
        String[] groups = withoutZone.split(":", -1);
        if (groups.length < 3 || groups[0].isEmpty() && groups[1].isEmpty()) {
            return "unknown";
        }
        return groups[0] + ":" + groups[1] + ":" + groups[2] + "::/48";
    }
}
