package com.spring.transfer.service;

import com.spring.transfer.common.MeterShift;
import com.spring.transfer.dto.MeterReadingRequest;
import com.spring.transfer.entity.MeterReading;
import com.spring.transfer.entity.ProductionLine;
import com.spring.transfer.repository.MeterReadingRepository;
import com.spring.transfer.repository.ProductionLineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 产线电表抄录服务。
 *
 * 每条产线每个班次（白班/夜班）留下一条电表抄录，记下班次、读数、抄表人与抄表时间；
 * 提交时与该产线上一条抄录比对，读数跳变绝对值超过约定幅度
 * （{@link MeterReading#JUMP_THRESHOLD_KWH}）的本条标记为异常。
 * 夜班复核（签发）前，该产线当天必须已有白班抄录，否则拦截。
 */
@Service
@RequiredArgsConstructor
public class MeterReadingService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final MeterReadingRepository meterReadingRepository;
    private final ProductionLineRepository productionLineRepository;

    /**
     * 提交电表抄录：产线、班次、读数、抄表人四项由 Bean Validation 强校验。
     * 每条产线每个班次当天只能提交一条；与上一条比对跳变，超过约定幅度标异常（仍如实保存）。
     */
    @Transactional
    public MeterReading submit(MeterReadingRequest request) {
        ProductionLine line = productionLineRepository.findById(request.getLineId())
                .orElseThrow(() -> new RuntimeException("抄表产线不存在，ID: " + request.getLineId()));

        LocalDate today = LocalDate.now(ZONE);
        MeterShift shift = request.getShift();
        if (meterReadingRepository.existsByLineIdAndReadingDateAndShift(line.getId(), today, shift)) {
            throw new RuntimeException("产线「" + line.getLineName() + "」今日（" + today.format(DATE_FORMATTER)
                    + "）" + shift.getLabel() + "已有电表抄录，每条产线每个班次只能留一条，请勿重复提交");
        }

        BigDecimal value = request.getReadingValue();
        // 上一条抄录：可能是当天更早班次，也可能是历史最近一条（跨天首抄仍与上一条比对）
        Optional<MeterReading> previousOpt =
                meterReadingRepository.findTopByLineIdOrderByReadTimeDescIdDesc(line.getId());

        MeterReading reading = new MeterReading();
        reading.setReadingNo(generateReadingNo());
        reading.setLineId(line.getId());
        reading.setLineCode(line.getLineCode());
        reading.setLineName(line.getLineName());
        reading.setReadingDate(today);
        reading.setShift(shift);
        reading.setReadingValue(value);
        reading.setReader(request.getReader().trim());
        reading.setReadTime(LocalDateTime.now(ZONE));
        reading.setJumpThreshold(MeterReading.JUMP_THRESHOLD_KWH);

        if (previousOpt.isPresent()) {
            MeterReading previous = previousOpt.get();
            BigDecimal delta = value.subtract(previous.getReadingValue());
            reading.setPreviousValue(previous.getReadingValue());
            reading.setJumpDelta(delta);
            // 约定「跳变超过幅度」：绝对值严格大于阈值；首条抄录没有上一条，不标异常
            if (delta.abs().compareTo(MeterReading.JUMP_THRESHOLD_KWH) > 0) {
                reading.setAbnormal(true);
                String direction = delta.signum() < 0 ? "回退" : "突增";
                reading.setAbnormalReason("读数较上一条" + direction + " " + delta.abs().stripTrailingZeros().toPlainString()
                        + " kWh（上一条 " + previous.getReadingValue().stripTrailingZeros().toPlainString()
                        + "，本条 " + value.stripTrailingZeros().toPlainString()
                        + "），超过约定幅度 " + MeterReading.JUMP_THRESHOLD_KWH.stripTrailingZeros().toPlainString()
                        + " kWh，请现场复核电表");
            } else {
                reading.setAbnormal(false);
            }
        } else {
            reading.setAbnormal(false);
        }
        return meterReadingRepository.save(reading);
    }

    /** 该产线的抄录历史（抄表时间倒序） */
    public List<MeterReading> findByLine(Long lineId) {
        return meterReadingRepository.findByLineIdOrderByReadTimeDescIdDesc(lineId);
    }

    /** 该产线最近一条抄录 */
    public Optional<MeterReading> latestOfLine(Long lineId) {
        return meterReadingRepository.findTopByLineIdOrderByReadTimeDescIdDesc(lineId);
    }

    /** 全部产线的最近一条抄录，key 为产线ID（列表一次性挂接，避免逐线查询） */
    public Map<Long, MeterReading> latestMap() {
        return meterReadingRepository.findAll().stream()
                .collect(Collectors.toMap(MeterReading::getLineId, Function.identity(),
                        (a, b) -> a.getReadTime().isAfter(b.getReadTime())
                                || (a.getReadTime().isEqual(b.getReadTime()) && a.getId() > b.getId()) ? a : b));
    }

    /** 该产线指定日期的白班抄录 */
    public Optional<MeterReading> findDayShift(Long lineId, LocalDate date) {
        return meterReadingRepository.findByLineIdAndReadingDateAndShift(lineId, date, MeterShift.DAY);
    }

    /** 指定日期各产线白班抄录，key 为产线ID（夜班复核批量判定当天白班是否已抄） */
    public Map<Long, MeterReading> dayShiftMap(LocalDate date) {
        return meterReadingRepository.findByReadingDateAndShift(date, MeterShift.DAY).stream()
                .collect(Collectors.toMap(MeterReading::getLineId, Function.identity(), (a, b) -> a));
    }

    /**
     * 夜班复核提交（签发）守卫：该产线当天若还没有白班抄录则不能交。
     */
    public void assertDayShiftReadingExists(ProductionLine line, LocalDate date) {
        if (meterReadingRepository.findByLineIdAndReadingDateAndShift(
                line.getId(), date, MeterShift.DAY).isEmpty()) {
            throw new RuntimeException("产线「" + line.getLineName() + "」当天（" + date.format(DATE_FORMATTER)
                    + "）还没有白班电表抄录，夜班复核不能提交；请先补抄当天白班读数后再签发");
        }
    }

    private String generateReadingNo() {
        String no;
        do {
            no = "MR" + LocalDateTime.now(ZONE).format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                    + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
        } while (meterReadingRepository.existsByReadingNo(no));
        return no;
    }
}
