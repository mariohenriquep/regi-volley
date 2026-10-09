package com.regivolley.api.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Threat model 10.11: the last seat through the controller. Members book the one remaining seat at the same moment over HTTP; the
 * controller adds no non-transactional step, so exactly one is confirmed, the others join the waitlist, and nobody gets a 5xx.
 */
class ConcurrentBookingOverHttpIntegrationTest extends AbstractApiIntegrationTest {

    @Test
    void membersBookingTheLastSeatAtTheSameMomentGetOneSeatAndTheRestTheWaitlist() throws Exception {
        // Arrange
        TenantWorld world = seedWorld();
        String admin = world.adminToken();
        expect(200, HttpMethod.PUT, "/api/v1/sessions/" + world.sessionId() + "/capacity", admin, "{\"capacity\":1}");
        expect(200, HttpMethod.POST, "/api/v1/sessions/" + world.sessionId() + "/bookings/" + world.bookingId() + "/cancellation", world.member().token(), null);
        List<Person> contenders = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Person person = joinAndActivate(world.tenant(), "Contender " + (char) ('A' + i));
            expect(201, HttpMethod.POST, "/api/v1/members/" + person.memberId() + "/subscriptions", admin,
                    "{\"planId\":\"" + world.planId() + "\",\"startDate\":\"2026-10-12\"}");
            contenders.add(person);
        }
        ExecutorService pool = Executors.newFixedThreadPool(contenders.size());
        CountDownLatch go = new CountDownLatch(1);
        List<Future<MvcResult>> answers = new ArrayList<>();

        // Act
        for (Person person : contenders) {
            Callable<MvcResult> book = () -> {
                go.await();
                return send(HttpMethod.POST, "/api/v1/sessions/" + world.sessionId() + "/bookings", person.token(), null);
            };
            answers.add(pool.submit(book));
        }
        go.countDown();
        List<MvcResult> results = new ArrayList<>();
        for (Future<MvcResult> answer : answers) {
            results.add(answer.get());
        }
        pool.shutdown();

        // Assert
        List<String> statuses = new ArrayList<>();
        List<Integer> positions = new ArrayList<>();
        for (MvcResult result : results) {
            assertThat(result.getResponse().getStatus()).as(body(result)).isEqualTo(201);
            statuses.add(read(result, "$.status"));
            if ("WAITLISTED".equals(read(result, "$.status"))) {
                positions.add(read(result, "$.waitlistPosition"));
            }
        }
        assertThat(statuses).containsOnlyOnce("CONFIRMED").containsOnly("CONFIRMED", "WAITLISTED");
        // (the clock is frozen here, so the three requests share one instant and their places among equals go by id: the places
        // the callers were told are each valid when told, which is why only the final state is compared)
        assertThat(positions).hasSize(3).allSatisfy(position -> assertThat(position).isBetween(1, 3));
        assertThat(jdbc.queryForObject("select count(*) from bookings where session_id = ?::uuid and status = 'WAITLISTED'", Integer.class, world.sessionId())).isEqualTo(3);
        assertThat(jdbc.queryForObject("select count(*) from bookings where session_id = ?::uuid and status = 'CONFIRMED'", Integer.class, world.sessionId())).isEqualTo(1);
    }
}
