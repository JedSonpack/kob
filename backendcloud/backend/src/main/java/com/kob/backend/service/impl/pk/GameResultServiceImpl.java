package com.kob.backend.service.impl.pk;

import com.kob.backend.mapper.RecordMapper;
import com.kob.backend.mapper.UserMapper;
import com.kob.backend.pojo.Record;
import com.kob.backend.pojo.User;
import com.kob.backend.service.pk.GameResultService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Service
public class GameResultServiceImpl implements GameResultService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RecordMapper recordMapper;

    @Transactional
    @Override
    public void saveResult(Integer aId, Integer bId, Integer aSx, Integer aSy,
                           Integer bSx, Integer bSy, String aSteps, String bSteps,
                           String map, String loser, Date createtime) {
        User a = userMapper.selectById(aId);
        User b = userMapper.selectById(bId);
        Integer ratingA = a.getRating();
        Integer ratingB = b.getRating();

        if ("A".equals(loser)) {       // A 输：A 扣 2，B 加 5
            ratingA -= 2;
            ratingB += 5;
        } else if ("B".equals(loser)) { // B 输：A 加 5，B 扣 2
            ratingA += 5;
            ratingB -= 2;
        }

        a.setRating(ratingA);
        b.setRating(ratingB);
        userMapper.updateById(a);
        userMapper.updateById(b);

        Record record = new Record(null, aId, aSx, aSy, bId, bSx, bSy,
                aSteps, bSteps, map, loser, createtime);
        recordMapper.insert(record);
    }
}
