package club.boyuan.official.domain.interview.snapshot;

import club.boyuan.official.common.utils.JwtTokenUtil;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
class EvaluationActor {
    record Identity(int id, String username) {}

    private final JwtTokenUtil jwt;
    private final RedisTemplate<String, Object> redis;

    EvaluationActor(JwtTokenUtil jwt, RedisTemplate<String, Object> redis) {
        this.jwt = jwt;
        this.redis = redis;
    }

    Identity verify(String token) {
        if (token == null || token.isBlank() || token.length() > 16384) throw unauthorized();
        try {
            Claims claims = jwt.extractClaim(token, value -> value);
            var id = claims.get("userId", Integer.class);
            if (id == null
                    || id <= 0
                    || claims.getSubject() == null
                    || claims.getSubject().isBlank()
                    || claims.getExpiration() == null
                    || !claims.getExpiration().after(new Date())
                    || jwt.isTokenRevoked(token)
                    || Boolean.TRUE.equals(redis.hasKey("jwt:blacklist:" + token))) {
                throw unauthorized();
            }
            return new Identity(id, claims.getSubject());
        } catch (JwtException | IllegalArgumentException malformed) {
            throw unauthorized();
        }
    }

    private static SnapshotFailure unauthorized() {
        return new SnapshotFailure(HttpStatus.UNAUTHORIZED, "评价用户凭据无效");
    }
}
