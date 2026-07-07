package com.pulseflow.backend.events;

import com.pulseflow.backend.analytics.dto.DailyStats;
import com.pulseflow.backend.analytics.dto.TopEvent;
import com.pulseflow.backend.analytics.dto.TopUser;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface EventRepository extends JpaRepository<Event, Long>, JpaSpecificationExecutor<Event> {

    @Query("SELECT new com.pulseflow.backend.analytics.dto.DailyStats(CAST(e.receivedAt AS localdate), COUNT(e)) " +
           "FROM Event e " +
           "WHERE e.receivedAt >= :startDate " +
           "GROUP BY CAST(e.receivedAt AS localdate) " +
           "ORDER BY CAST(e.receivedAt AS localdate) ASC")
    List<DailyStats> countEventsByDate(LocalDateTime startDate);

    @Query("SELECT new com.pulseflow.backend.analytics.dto.TopEvent(CAST(e.eventType AS string), COUNT(e)) " +
           "FROM Event e " +
           "GROUP BY e.eventType " +
           "ORDER BY COUNT(e) DESC")
    List<TopEvent> findTopEvents(Pageable pageable);

    @Query("SELECT new com.pulseflow.backend.analytics.dto.TopUser(e.userId, COUNT(e)) " +
           "FROM Event e " +
           "WHERE e.userId IS NOT NULL " +
           "GROUP BY e.userId " +
           "ORDER BY COUNT(e) DESC")
    List<TopUser> findTopUsers(Pageable pageable);

    List<Event> findTop50ByOrderByReceivedAtDesc();
}
