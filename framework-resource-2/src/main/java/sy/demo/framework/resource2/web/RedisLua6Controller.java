package sy.demo.framework.resource2.web;

import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sy.demo.framework.resource2.config.RedConstants;

import java.util.Arrays;
import java.util.List;

/**
 * Created by dell on 2018/12/27.
 * @author dell
 */
@RestController
@RequestMapping("/lua6")
public class RedisLua6Controller {

    @Autowired
    private RedisTemplate redisTemplate;

    private static DefaultRedisScript defaultRedisScript = new DefaultRedisScript();
    static {
        defaultRedisScript.setResultType(String.class);
        defaultRedisScript.setScriptText("math.randomseed(KEYS[6]:reverse():sub(1,6)) \n" +
                "local function getRandomMoney( remainSize, remainMoney, minPrice, maxPrice) \n" +
                "if (remainSize <=0 or remainMoney <=0 ) then \n" +
                "return nil \n" +
                "end \n" +
                "if (minPrice == 0 or minPrice == nil) then \n" +
                "minPrice = 1 \n" +
                "end \n" +
                "if (maxPrice == 0 or maxPrice == nil) then \n" +
                "maxPrice = remainMoney \n" +
                "end \n" +
                "local minTemp = remainMoney - (maxPrice * (remainSize-1)) \n" +
                "local randomMin = minTemp \n" +
                "if (minTemp < minPrice) then \n" +
                "randomMin = minPrice \n" +
                "end \n" +
                "local maxTemp = remainMoney - (minPrice * (remainSize-1)) \n" +
                "local randomMax = maxTemp  \n" +
                "if (maxTemp > maxPrice) then \n" +
                "randomMax = maxPrice \n" +
                "end \n" +
                "local beatMax = remainMoney / remainSize * 2 \n" +
                "if (randomMax > beatMax) then \n" +
                "randomMax = beatMax \n" +
                "end \n" +
                "return math.floor((math.random()*(randomMax-randomMin) + randomMin)+0.5); \n" +
                "end \n" +
                "if redis.call('hexists', KEYS[7], KEYS[8]) ~= 0 then \n" +
                "return nil\n" +
                "else\n" +
                "local size = tonumber(redis.call('hget', KEYS[1], KEYS[2])) \n" +
                "local money = tonumber(redis.call('hget', KEYS[1], KEYS[3])) \n" +
                "local maxPrice = tonumber(redis.call('hget', KEYS[1], KEYS[4])) \n" +
                "local minPrice = tonumber(redis.call('hget', KEYS[1], KEYS[5])) \n" +
                "if (size <= 0 or money <= 0)then \n" +
                "return tostring(0) \n" +
                "else \n" +
                "local randomMoney = getRandomMoney( size, money,minPrice,maxPrice) \n" +
                "redis.call('hincrby', KEYS[1], KEYS[2], -1) \n" +
                "redis.call('hincrbyfloat', KEYS[1], KEYS[3], -randomMoney) \n" +
                "redis.call('hmset', KEYS[7],KEYS[8],randomMoney) \n" +
                "return tostring(randomMoney) \n" +
                "end\n" +
                "end");
    }

    public OutUnpackDto unpack(Long redId, String userAccount) throws Exception {
        OutUnpackDto outUnpackDto = new OutUnpackDto();
        List<String> list = redisTemplate.opsForHash().multiGet(RedConstants.HB_INFO+redId,
                Arrays.asList(RedConstants.HB_DEADLINE,RedConstants.HB_TYPE));
        if (null != list && list.size() > 0){
            String deadLine = list.get(0);
            if (System.currentTimeMillis() > Long.parseLong(deadLine)){
                outUnpackDto.setCanUnpackFlag(false);
                outUnpackDto.setMsg("红包已经过期了");
                return outUnpackDto;
            }
            String type = list.get(1);
            if ("luck".equals(type)){

                Object object = redisTemplate.execute(defaultRedisScript,
                        Arrays.asList(RedConstants.HB_INFO+redId, RedConstants.HB_SIZE,
                                RedConstants.HB_MONEY, RedConstants.HB_MAX_PRICE, RedConstants.HB_MIN_PRICE,
                                String.valueOf(System.currentTimeMillis()),RedConstants.HB_USER+redId,userAccount));

                if (null == object){
                    outUnpackDto.setCanUnpackFlag(false);
                    outUnpackDto.setMsg("你已经抢过红包了，不能再抢了");
                    return outUnpackDto;
                }else{
                    int redMoney = Integer.parseInt(object.toString());
                    if (redMoney == 0){
                        outUnpackDto.setCanUnpackFlag(false);
                        outUnpackDto.setMsg("手慢了，红包已经被抢完了");
                        return outUnpackDto;
                    }else{
                        // TODO 拆红包记录异步入库，异步入余额
                        // TODO 给抢红包和发红包者，发送消息。
                        // TODO 最后一个红包被抢后，给发红包者发送被抢完消息，其他处理（redis中的数据怎么处理）
                        outUnpackDto.setCanUnpackFlag(true);
                        outUnpackDto.setMsg("恭喜你，抢到了");
                        outUnpackDto.setMoney(redMoney);
                        return outUnpackDto;
                    }
                }
            }else if ("normal".equals(type)){
                // TODO 普通红包处理
            }
        }
        outUnpackDto.setCanUnpackFlag(false);
        outUnpackDto.setMsg("休息一下再抢吧");
        return outUnpackDto;
    }

    @Data
    public class OutUnpackDto {

        private boolean canUnpackFlag;

        private String msg;

        /**
         * 抢了多少钱，单位分
         */
        private Integer money;
    }

}
