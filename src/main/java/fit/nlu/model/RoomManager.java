package fit.nlu.model;

import org.springframework.data.redis.core.RedisTemplate;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RoomManager {
    private ConcurrentHashMap<UUID, Room> rooms;
    private RedisTemplate redisTemplate;
}
