package com.ogani.store.auth.reset;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class ResetRateLimiter {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final PasswordResetProperties props;
    private record Bucket(LocalDateTime start,int count,LocalDateTime last) {}
    public record Permit(String bucketKey,LocalDateTime windowStart,int sequence) {}

    // Commit rate usage even when the caller rejects a bad/expired token afterward.
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public boolean consume(String key,int limit,int cooldownSeconds) {
        return acquire(key,limit,cooldownSeconds)!=null;
    }

    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public Permit acquire(String key,int limit,int cooldownSeconds) {
        String hash=ResetTokenCodec.hash(key);LocalDateTime now=LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
        jdbc.update("insert into password_reset_rate_limits(bucket_key,window_started_at,request_count) values(?,?,0) on conflict do nothing",hash,now);
        var row=jdbc.queryForObject("select window_started_at,request_count,last_accepted_at from password_reset_rate_limits where bucket_key=? for update",
                (rs,n) -> new Bucket(rs.getObject(1,LocalDateTime.class),rs.getInt(2),rs.getObject(3,LocalDateTime.class)),hash);
        LocalDateTime start=row.start(),last=row.last();int count=row.count();
        if(!start.plusMinutes(props.windowMinutes()).isAfter(now)) {start=now;last=null;count=0;}
        if(count>=limit || (last!=null && now.isBefore(last.plusSeconds(cooldownSeconds)))) return null;
        jdbc.update("update password_reset_rate_limits set window_started_at=?,request_count=?,last_accepted_at=? where bucket_key=?",start,count+1,now,hash);
        return new Permit(hash,start,count+1);
    }

    @Transactional(propagation=Propagation.MANDATORY)
    public boolean isCurrent(Permit permit) {
        return jdbc.queryForObject("select count(*) from password_reset_rate_limits where bucket_key=? and window_started_at=? and request_count=?",
                Integer.class,permit.bucketKey(),permit.windowStart(),permit.sequence())==1;
    }
}
