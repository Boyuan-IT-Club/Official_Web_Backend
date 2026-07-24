package club.boyuan.official.infra.seckill;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SeckillLuaResultTest {

    @Test
    void fromCode_mapsKnownCodes() {
        assertEquals(SeckillLuaResult.SUCCESS, SeckillLuaResult.fromCode(1));
        assertEquals(SeckillLuaResult.FULL, SeckillLuaResult.fromCode(0));
        assertEquals(SeckillLuaResult.CACHE_MISS, SeckillLuaResult.fromCode(-1));
        assertEquals(SeckillLuaResult.USER_LOCKED, SeckillLuaResult.fromCode(-2));
    }

    @Test
    void fromCode_unknownFallsBackToCacheMiss() {
        assertEquals(SeckillLuaResult.CACHE_MISS, SeckillLuaResult.fromCode(999));
        assertEquals(SeckillLuaResult.CACHE_MISS, SeckillLuaResult.fromCode(-100));
    }
}
