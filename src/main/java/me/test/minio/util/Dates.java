package me.test.minio.util;


import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static me.test.minio.util.Dates.Pattern.*;

/**
 * 日期处理类
 */
@Slf4j
public final class Dates {
    private static final ZoneId ZONE_ID = ZoneId.of("GMT+8");

    public interface IPattern {
        /**
         * 日期 + 时间 格式化对象
         *
         * @return {@link DateTimeFormatter}
         */
        DateTimeFormatter getFormatter();

        /**
         * 转换为日期操作对象
         *
         * @param value String 日期
         * @return {@link Dates}
         */
        Dates parse(final String value);

        /**
         * 获取日期字符
         *
         * @return {@link String}
         */
        String now();

        /**
         * 格式化日期
         *
         * @param value {@link LocalDateTime}
         * @return {@link String}
         */
        String format(final LocalDateTime value);

        /**
         * 格式化日期
         *
         * @param value {@link LocalDate}
         * @return {@link String}
         */
        String format(final LocalDate value);

        /**
         * 格式化日期
         *
         * @param value {@link LocalTime}
         * @return {@link String}
         */
        String format(final LocalTime value);

        /**
         * 格式化日期
         *
         * @param value {@link Timestamp}
         * @return {@link String}
         */
        String format(final Timestamp value);

        /**
         * 格式化日期
         *
         * @param value {@link Date} or {@link Timestamp}
         * @return {@link String}
         */
        String format(final Date value);
    }

    public interface IDateTimePatternAdapter extends IPattern {
        @Override
        default String now() {
            return LocalDateTime.now().format(getFormatter());
        }

        @Override
        default String format(final LocalDateTime value) {
            return value.format(getFormatter());
        }

        @Override
        default String format(final LocalDate value) {
            return value.atStartOfDay().format(getFormatter());
        }

        @Override
        default String format(final LocalTime value) {
            return value.atDate(LocalDate.now()).format(getFormatter());
        }

        @Override
        default String format(final Timestamp value) {
            return value.toLocalDateTime().format(getFormatter());
        }

        @Override
        default String format(final Date value) {
            return getFormatter().format(value.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
        }
    }

    public interface IDatePatternAdapter extends IPattern {
        @Override
        default String now() {
            return LocalDate.now().format(getFormatter());
        }

        @Override
        default String format(final LocalDateTime value) {
            return value.toLocalDate().format(getFormatter());
        }

        @Override
        default String format(final LocalDate value) {
            return value.format(getFormatter());
        }

        @Override
        default String format(final LocalTime value) {
            throw new IllegalArgumentException("无法将 LocalTime 格式化成 LocalDate");
        }

        @Override
        default String format(final Timestamp value) {
            return value.toLocalDateTime().toLocalDate().format(getFormatter());
        }

        @Override
        default String format(final Date value) {
            return getFormatter().format(value.toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
        }
    }

    public interface ITimePatternAdapter extends IPattern {
        @Override
        default String now() {
            return LocalTime.now().format(getFormatter());
        }

        @Override
        default String format(final LocalDateTime value) {
            return value.toLocalTime().format(getFormatter());
        }

        @Override
        default String format(final LocalDate value) {
            throw new IllegalArgumentException("无法将 LocalDate 格式化成 LocalTime");
        }

        @Override
        default String format(final LocalTime value) {
            return value.format(getFormatter());
        }

        @Override
        default String format(final Timestamp value) {
            return value.toLocalDateTime().toLocalTime().format(getFormatter());
        }

        @Override
        default String format(final Date value) {
            return getFormatter().format(value.toInstant().atZone(ZoneId.systemDefault()).toLocalTime());
        }
    }

    /**
     * 枚举：定义日期格式
     */
    public enum Pattern implements IPattern {
        yyyy_MM_dd_HH_mm_ss_SSS("yyyy-MM-dd HH:mm:ss.SSS", new IDateTimePatternAdapter() {
            @Override
            public DateTimeFormatter getFormatter() {
                return yyyy_MM_dd_HH_mm_ss_SSS.formatter;
            }

            @Override
            public Dates parse(final String value) {
                try {
                    return Dates.of(LocalDateTime.parse(value, getFormatter()));
                } catch (DateTimeParseException e) {
                    try {
                        return Dates.of(LocalDateTime.parse(value, yyyy_MM_dd_HH_mm_ss.formatter));
                    } catch (DateTimeParseException ex) {
                        return Dates.of(LocalDate.parse(value, yyyy_MM_dd.formatter));
                    }
                }
            }
        }),
        yyyy_MM_dd_HH_mm_ss("yyyy-MM-dd HH:mm:ss", new IDateTimePatternAdapter() {
            private final java.util.regex.Pattern PATTERN = java.util.regex.Pattern.compile("(\\d{4})-(\\d+)-(\\d+) (\\d+):(\\d+):(\\d+).*");

            @Override
            public DateTimeFormatter getFormatter() {
                return yyyy_MM_dd_HH_mm_ss.formatter;
            }

            @Override
            public Dates parse(final String value) {
                try {
                    return Dates.of(LocalDateTime.parse(value, getFormatter()));
                } catch (DateTimeParseException e) {
                    try {
                        return Dates.of(LocalDateTime.parse(value, yyyy_MM_dd_HH_mm_ss_SSS.formatter));
                    } catch (DateTimeParseException ex) {
                        try {
                            return Dates.of(LocalDate.parse(value, yyyy_MM_dd.formatter));
                        } catch (Exception e1) {
                            final Matcher matcher = PATTERN.matcher(value);
                            if (matcher.find()) {
                                return Dates.of(LocalDateTime.of(
                                        Integer.parseInt(matcher.group(1)),
                                        Integer.parseInt(matcher.group(2)),
                                        Integer.parseInt(matcher.group(3)),
                                        Integer.parseInt(matcher.group(4)),
                                        Integer.parseInt(matcher.group(5)),
                                        Integer.parseInt(matcher.group(6))
                                ));
                            } else {
                                throw new DateTimeException("格式解析失败:".concat(value));
                            }
                        }
                    }
                }
            }
        }),
        yyyy_MM_dd("yyyy-MM-dd", new IDatePatternAdapter() {
            private final java.util.regex.Pattern PATTERN = java.util.regex.Pattern.compile("(\\d{4})-(\\d+)-(\\d+).*");

            @Override
            public DateTimeFormatter getFormatter() {
                return yyyy_MM_dd.formatter;
            }

            @Override
            public Dates parse(final String value) {
                try {
                    return Dates.of(LocalDate.parse(value, getFormatter()));
                } catch (DateTimeParseException e) {
                    try {
                        return Dates.of(LocalDateTime.parse(value, yyyy_MM_dd_HH_mm_ss_SSS.formatter));
                    } catch (DateTimeParseException ex) {
                        try {
                            return Dates.of(LocalDateTime.parse(value, yyyy_MM_dd_HH_mm_ss.formatter));
                        } catch (Exception exc) {
                            final Matcher matcher = PATTERN.matcher(value);
                            if (matcher.find()) {
                                return Dates.of(LocalDate.of(
                                        Integer.parseInt(matcher.group(1)),
                                        Integer.parseInt(matcher.group(2)),
                                        Integer.parseInt(matcher.group(3))
                                ));
                            } else {
                                throw new DateTimeException("格式解析失败:".concat(value));
                            }
                        }
                    }
                }
            }
        }),
        yyyy_MM("yyyy-MM", new IDatePatternAdapter() {
            private final java.util.regex.Pattern PATTERN = java.util.regex.Pattern.compile("(\\d{4})-(\\d+).*");

            @Override
            public DateTimeFormatter getFormatter() {
                return yyyy_MM.formatter;
            }

            @Override
            public Dates parse(final String value) {
                try {
                    return Dates.of(YearMonth.parse(value, getFormatter()).atDay(1));
                } catch (Exception e) {
                    try {
                        return yyyy_MM_dd.parse(value);
                    } catch (Exception exc) {
                        final Matcher matcher = PATTERN.matcher(value);
                        if (matcher.find()) {
                            return Dates.of(LocalDate.of(
                                    Integer.parseInt(matcher.group(1)),
                                    Integer.parseInt(matcher.group(2)),
                                    0
                            ));
                        } else {
                            throw new DateTimeException("格式解析失败:".concat(value));
                        }
                    }
                }
            }
        }),
        yy_MM_dd("yy-MM-dd", new IDatePatternAdapter() {
            private final java.util.regex.Pattern PATTERN = java.util.regex.Pattern.compile("(\\d{2})-(\\d+)-(\\d+).*");

            @Override
            public DateTimeFormatter getFormatter() {
                return yy_MM_dd.formatter;
            }

            @Override
            public Dates parse(final String value) {
                try {
                    return Dates.of(LocalDate.parse(value, getFormatter()));
                } catch (Exception e) {
                    final Matcher matcher = PATTERN.matcher(value);
                    if (matcher.find()) {
                        return Dates.of(LocalDate.of(
                                Integer.parseInt("20".concat(matcher.group(1))),
                                Integer.parseInt(matcher.group(2)),
                                Integer.parseInt(matcher.group(3))
                        ));
                    } else {
                        throw new DateTimeException("格式解析失败:".concat(value));
                    }
                }
            }
        }),
        HH_mm_ss("HH:mm:ss", new ITimePatternAdapter() {
            private final java.util.regex.Pattern PATTERN = java.util.regex.Pattern.compile("(\\d+):(\\d+):(\\d+).*");

            @Override
            public DateTimeFormatter getFormatter() {
                return HH_mm_ss.formatter;
            }

            @Override
            public Dates parse(final String value) {
                try {
                    return Dates.of(LocalTime.parse(value, getFormatter()));
                } catch (DateTimeParseException e) {
                    try {
                        return Dates.of(LocalDateTime.parse(value, yyyy_MM_dd_HH_mm_ss_SSS.formatter));
                    } catch (DateTimeParseException ex) {
                        try {
                            return Dates.of(LocalDateTime.parse(value, yyyy_MM_dd_HH_mm_ss.formatter));
                        } catch (Exception exc) {
                            final Matcher matcher = PATTERN.matcher(value);
                            if (matcher.find()) {
                                return Dates.of(LocalTime.of(
                                        Integer.parseInt(matcher.group(1)),
                                        Integer.parseInt(matcher.group(2)),
                                        Integer.parseInt(matcher.group(3))
                                ));
                            } else {
                                throw new DateTimeException("格式解析失败:".concat(value));
                            }
                        }
                    }
                }
            }
        }),
        HH_mm("HH:mm", new ITimePatternAdapter() {
            private final java.util.regex.Pattern PATTERN = java.util.regex.Pattern.compile("(\\d+):(\\d+).*");

            @Override
            public DateTimeFormatter getFormatter() {
                return HH_mm.formatter;
            }

            @Override
            public Dates parse(final String value) {
                try {
                    return Dates.of(LocalTime.parse(value, getFormatter()));
                } catch (Exception e) {
                    try {
                        return Dates.of(LocalTime.parse(value));
                    } catch (Exception exc) {
                        final Matcher matcher = PATTERN.matcher(value);
                        if (matcher.find()) {
                            return Dates.of(LocalTime.of(
                                    Integer.parseInt(matcher.group(1)),
                                    Integer.parseInt(matcher.group(2)),
                                    0
                            ));
                        } else {
                            throw new DateTimeException("格式解析失败:".concat(value));
                        }
                    }
                }
            }
        }),
        yyyyMMddHHmmssSSS("yyyyMMddHHmmssSSS",
                new DateTimeFormatterBuilder().appendPattern("yyyyMMddHHmmss").appendValue(ChronoField.MILLI_OF_SECOND, 3).toFormatter(),
                new IDateTimePatternAdapter() {
                    @Override
                    public DateTimeFormatter getFormatter() {
                        return yyyyMMddHHmmssSSS.formatter;
                    }

                    @Override
                    public Dates parse(final String value) {
                        return Dates.of(LocalDateTime.parse(value, getFormatter()));
//                try {
//                    return Dates.of(LocalDateTime.parse(value, getFormatter()));
//                } catch (DateTimeParseException e) {
//                    try {
//                        return Dates.of(LocalDateTime.parse(value, yyyyMMddHHmmss.formatter));
//                    } catch (DateTimeParseException ex) {
//                        return Dates.of(LocalDate.parse(value, yyyyMMdd.formatter));
//                    }
//                }
                    }
                }),
        yyyyMMddHHmmss("yyyyMMddHHmmss", new IDateTimePatternAdapter() {
            @Override
            public DateTimeFormatter getFormatter() {
                return yyyyMMddHHmmss.formatter;
            }

            @Override
            public Dates parse(final String value) {
                try {
                    return Dates.of(LocalDateTime.parse(value, getFormatter()));
                } catch (DateTimeParseException e) {
                    try {
                        return Dates.of(LocalDateTime.parse(value, yyyyMMddHHmmssSSS.formatter));
                    } catch (DateTimeParseException ex) {
                        return Dates.of(LocalDate.parse(value, yyyyMMdd.formatter));
                    }
                }
            }
        }),
        yyyyMMdd("yyyyMMdd", new IDatePatternAdapter() {
            @Override
            public DateTimeFormatter getFormatter() {
                return yyyyMMdd.formatter;
            }

            @Override
            public Dates parse(final String value) {
                try {
                    return Dates.of(LocalDate.parse(value, getFormatter()));
                } catch (DateTimeParseException e) {
                    try {
                        return Dates.of(LocalDateTime.parse(value, yyyyMMddHHmmssSSS.formatter));
                    } catch (DateTimeParseException ex) {
                        return Dates.of(LocalDateTime.parse(value, yyyyMMddHHmmss.formatter));
                    }
                }
            }
        }),
        yyyyMM("yyyyMM", new IDatePatternAdapter() {
            @Override
            public DateTimeFormatter getFormatter() {
                return yyyyMM.formatter;
            }

            @Override
            public Dates parse(final String value) {
                return Dates.of(YearMonth.parse(value, getFormatter()).atDay(1));
            }
        }),
        HHmmssSSS("HHmmssSSS", new ITimePatternAdapter() {
            @Override
            public DateTimeFormatter getFormatter() {
                return HHmmssSSS.formatter;
            }

            @Override
            public Dates parse(final String value) {
                try {
                    return Dates.of(LocalTime.parse(value, getFormatter()));
                } catch (DateTimeParseException e) {
                    try {
                        return Dates.of(LocalDateTime.parse(value, yyyyMMddHHmmssSSS.formatter));
                    } catch (DateTimeParseException ex) {
                        return Dates.of(LocalDateTime.parse(value, yyyyMMddHHmmss.formatter));
                    }
                }
            }
        }),
        HHmmss("HHmmss", new ITimePatternAdapter() {
            @Override
            public DateTimeFormatter getFormatter() {
                return HHmmss.formatter;
            }

            @Override
            public Dates parse(final String value) {
                return Dates.of(LocalTime.parse(value, getFormatter()));
            }
        }),

        U_yyyy_MM_dd_HH_mm_ss_SSS("yyyy/MM/dd HH:mm:ss.SSS", new IDateTimePatternAdapter() {
            @Override
            public DateTimeFormatter getFormatter() {
                return U_yyyy_MM_dd_HH_mm_ss_SSS.formatter;
            }

            @Override
            public Dates parse(final String value) {
                try {
//                    return Dates.of(LocalDateTime.parse(value.replace("/", "-"), yyyy_MM_dd_HH_mm_ss_SSS.formatter));
                    return Dates.of(LocalDateTime.parse(value, getFormatter()));
                } catch (DateTimeParseException e) {
                    try {
                        return Dates.of(LocalDateTime.parse(value, U_yyyy_MM_dd_HH_mm_ss.formatter));
                    } catch (DateTimeParseException ex) {
                        return Dates.of(LocalDate.parse(value, U_yyyy_MM_dd.formatter));
                    }
                }
            }
        }),
        U_yyyy_MM_dd_HH_mm_ss("yyyy/MM/dd HH:mm:ss", new IDateTimePatternAdapter() {

            private final java.util.regex.Pattern PATTERN = java.util.regex.Pattern.compile("(\\d{4})/(\\d+)/(\\d+) (\\d+):(\\d+):(\\d+).*");

            @Override
            public DateTimeFormatter getFormatter() {
                return U_yyyy_MM_dd_HH_mm_ss.formatter;
            }

            @Override
            public Dates parse(final String value) {
                try {
                    return Dates.of(LocalDateTime.parse(value, getFormatter()));
                } catch (DateTimeParseException e) {
                    try {
                        return Dates.of(LocalDateTime.parse(value, U_yyyy_MM_dd_HH_mm_ss_SSS.formatter));
                    } catch (DateTimeParseException ex) {
                        try {
                            return Dates.of(LocalDate.parse(value, U_yyyy_MM_dd.formatter));
                        } catch (DateTimeParseException exc) {
                            try {
                                return Dates.of(LocalDate.parse(value, yyyy_MM_dd.formatter));
                            } catch (Exception e1) {
                                final Matcher matcher = PATTERN.matcher(value);
                                if (matcher.find()) {
                                    return Dates.of(LocalDateTime.of(
                                            Integer.parseInt(matcher.group(1)),
                                            Integer.parseInt(matcher.group(2)),
                                            Integer.parseInt(matcher.group(3)),
                                            Integer.parseInt(matcher.group(4)),
                                            Integer.parseInt(matcher.group(5)),
                                            Integer.parseInt(matcher.group(6))
                                    ));
                                } else {
                                    throw new DateTimeException("格式解析失败:".concat(value));
                                }
                            }
                        }
                    }
                }
            }
        }),
        U_yyyy_MM_dd("yyyy/MM/dd", new IDatePatternAdapter() {
            private final java.util.regex.Pattern PATTERN = java.util.regex.Pattern.compile("(\\d{4})/(\\d+)/(\\d+).*");

            @Override
            public DateTimeFormatter getFormatter() {
                return U_yyyy_MM_dd.formatter;
            }

            @Override
            public Dates parse(final String value) {
                try {
                    return Dates.of(LocalDate.parse(value, getFormatter()));
                } catch (DateTimeParseException e) {
                    try {
                        return Dates.of(LocalDateTime.parse(value, U_yyyy_MM_dd_HH_mm_ss.formatter));
                    } catch (DateTimeParseException ex) {
                        try {
                            return Dates.of(LocalDateTime.parse(value, U_yyyy_MM_dd_HH_mm_ss_SSS.formatter));
                        } catch (Exception exc) {
                            final Matcher matcher = PATTERN.matcher(value);
                            if (matcher.find()) {
                                return Dates.of(LocalDate.of(
                                        Integer.parseInt(matcher.group(1)),
                                        Integer.parseInt(matcher.group(2)),
                                        Integer.parseInt(matcher.group(3))
                                ));
                            } else {
                                throw new DateTimeException("格式解析失败:".concat(value));
                            }
                        }
                    }
                }
            }
        }),

        zh_yyyy_MM_dd_HH_mm_ss("yyyy年MM月dd日 HH时mm分ss秒", new IDateTimePatternAdapter() {
            @Override
            public DateTimeFormatter getFormatter() {
                return zh_yyyy_MM_dd_HH_mm_ss.formatter;
            }

            @Override
            public Dates parse(final String value) {
                return Dates.of(LocalDateTime.parse(value, getFormatter()));
            }
        }),
        zh_yyyy_MM_dd_HH_mm("yyyy年MM月dd日 HH时mm分", new IDateTimePatternAdapter() {
            @Override
            public DateTimeFormatter getFormatter() {
                return zh_yyyy_MM_dd_HH_mm.formatter;
            }

            @Override
            public Dates parse(final String value) {
                return Dates.of(LocalDateTime.parse(value, getFormatter()));
            }
        }),
        zh_yyyy_MM_dd("yyyy年MM月dd日", new IDatePatternAdapter() {
            @Override
            public DateTimeFormatter getFormatter() {
                return zh_yyyy_MM_dd.formatter;
            }

            @Override
            public Dates parse(final String value) {
                return Dates.of(LocalDate.parse(value, getFormatter()));
            }
        }),
        zh_yyyy_MM("yyyy年MM月", new IDatePatternAdapter() {
            @Override
            public DateTimeFormatter getFormatter() {
                return zh_yyyy_MM.formatter;
            }

            @Override
            public Dates parse(final String value) {
                return Dates.of(YearMonth.parse(value, getFormatter()).atDay(1));
            }
        }),
        ;
        /**
         * 枚举属性说明
         */
        private final String comment;
        private final DateTimeFormatter formatter;
        /**
         * 不要使用非线程安全的 java 日期工具类
         */
        @Deprecated
        private final SimpleDateFormat format;
        /**
         * 日期+时间处理适配器
         */
        private final IPattern adapter;

        public String value() {
            return this.comment;
        }

        Pattern(final String comment, final IPattern adapter) {
            this(comment, DateTimeFormatter.ofPattern(comment), adapter);
        }

        Pattern(final String comment, final DateTimeFormatter dateTimeFormatter, final IPattern adapter) {
            this.comment = comment;
            this.formatter = dateTimeFormatter;
            this.format = new SimpleDateFormat(comment, Locale.CHINA);
            this.adapter = adapter;
        }

        /**
         * 不要使用非线程安全的 java 日期工具类
         */
        @Deprecated
        public SimpleDateFormat getFormat() {
            return format;
        }

        @Override
        public DateTimeFormatter getFormatter() {
            return this.formatter;
        }

        /**
         * 获取日期字符
         *
         * @return {@link String}
         */
        @Override
        public String now() {
            return adapter.now();
        }

        /**
         * 转换为日期操作对象
         *
         * @param value String 日期
         * @return {@link Dates}
         */
        @Override
        public Dates parse(final String value) {
            return Objects.requireNonNull(
                    adapter.parse(Objects.requireNonNull(value, "date string value is not null")),
                    "return Dates is not null"
            );
        }

        /**
         * 转换为日期操作对象
         *
         * @param value String 日期
         * @return {@link Dates}
         */
        public Optional<Dates> parseOfNullable(final String value) {
            return Optional.ofNullable(value)
                    .filter(v -> !Objects.equals("", v.trim()))
                    .map(adapter::parse);
        }

        /**
         * 格式化日期
         *
         * @param value {@link LocalDateTime}
         * @return {@link String}
         */
        @Override
        public String format(final LocalDateTime value) {
            return Objects.isNull(value) ? null : adapter.format(value);
        }

        /**
         * 格式化日期
         *
         * @param value {@link LocalDate}
         * @return {@link String}
         */
        @Override
        public String format(final LocalDate value) {
            return Objects.isNull(value) ? null : adapter.format(value);
        }

        /**
         * 格式化日期
         *
         * @param value {@link LocalTime}
         * @return {@link String}
         */
        @Override
        public String format(final LocalTime value) {
            return Objects.isNull(value) ? null : adapter.format(value);
        }

        /**
         * 格式化日期
         *
         * @param value {@link Timestamp}
         * @return {@link String}
         */
        @Override
        public String format(final Timestamp value) {
            return Objects.isNull(value) ? null : adapter.format(value);
        }

        /**
         * 格式化日期
         *
         * @param value {@link Date} or {@link Timestamp}
         * @return {@link String}
         */
        @Override
        public String format(final Date value) {
            return Objects.isNull(value) ? null : adapter.format(value);
        }
    }

    /**
     * 定义日期区间
     */
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Data
    @Accessors(chain = true)
    public static class Range {
        /**
         * 开始
         */
        @JSONField(format = "yyyy-MM-dd HH:mm:ss.SSS")
        private Timestamp begin;
        /**
         * 结束
         */
        @JSONField(format = "yyyy-MM-dd HH:mm:ss.SSS")
        private Timestamp end;

        /**
         * 以当天时间初始化区间 yyyy-MM-dd 00:00:00.000 - yyyy-MM-dd 23:59:59.999
         *
         * @return {@link Range}
         */
        public static Range today() {
            final Dates now = Dates.now();
            return Range.builder()
                    .begin(now.beginTimeOfDay().timestamp())
                    .end(now.endTimeOfDay().timestamp())
                    .build();
        }

        /**
         * 以当月时间初始化区间 yyyy-MM-01 00:00:00.00 - yyyy-MM-(28|30|31) 23:59:59.999
         *
         * @return {@link Range}
         */
        public static Range month() {
            final Dates now = Dates.now();
            return Range.builder()
                    .begin(now.firstDayOfMonth().beginTimeOfDay().timestamp())
                    .end(now.lastDayOfMonth().endTimeOfDay().timestamp())
                    .build();
        }

        /**
         * 遍历选定区间：按天
         *
         * @param action {@link BiConsumer}{@link BiConsumer<Timestamp:start, Timestamp:end> } <br>start=2018-01-01 00:00:00.000 <br>end=2018-01-01 23:59:59.999
         */
        public void forEach(BiConsumer<Timestamp, Timestamp> action) {
            Objects.requireNonNull(action, "参数【action】是必须的");
            final Dates beginDate = Dates.of(begin);
            final Dates endDate = Dates.of(end).endTimeOfDay();
            do {
                action.accept(beginDate.beginTimeOfDay().timestamp(), beginDate.endTimeOfDay().timestamp());
                beginDate.addDay(1);
            } while (beginDate.le(endDate));
        }

        /**
         * 遍历选定区间：按月
         *
         * @param action {@link BiConsumer}{@link BiConsumer<Timestamp:start, Timestamp:end> } <br>start=2018-01-01 00:00:00.000 <br>end=2018-01-31 23:59:59.999
         */
        public void forEachMonth(BiConsumer<Timestamp, Timestamp> action) {
            Objects.requireNonNull(action, "参数【action】是必须的");
            final Dates beginDate = Dates.of(begin);
            final Dates endDate = Dates.of(end).lastDayOfMonth();
            do {
                action.accept(beginDate.firstDayOfMonth().timestamp(), beginDate.lastDayOfMonth().timestamp());
                beginDate.addMonth(1);
            } while (beginDate.le(endDate));
        }

        /**
         * 保留年月日，将开始时间设置为 00:00:00.000
         * 保留年月日，将结束时间设置为 23:59:59.999
         *
         * @return {@link Range}
         */
        public Range rebuild() {
            if (Objects.nonNull(begin)) {
                begin = Dates.of(begin).beginTimeOfDay().timestamp();
            }
            if (Objects.nonNull(end)) {
                end = Dates.of(end).endTimeOfDay().timestamp();
            }
            return this;
        }

        /**
         * 校验开始时间必须小于结束时间
         *
         * @return {@link Boolean}
         */
        public boolean check() {
            try {
                check(null);
                return true;
            } catch (Exception e) {
                log.error(e.getMessage(), e);
                return false;
            }
        }

        /**
         * 校验开始时间必须小于结束时间
         *
         * @return {@link Range}
         */
        public Range check(final Supplier<? extends RuntimeException> exSupplier) {
            if (Objects.isNull(begin)) {
                if (Objects.isNull(exSupplier))
                    throw new NullPointerException("begin is null");
                else
                    throw exSupplier.get();
            }
            if (Objects.nonNull(end)) {
                if (Dates.of(begin).gt(Dates.of(end))) {
                    if (Objects.isNull(exSupplier))
                        throw new RuntimeException("begin > end");
                    else
                        throw exSupplier.get();
                }
            } else {
                end = Dates.now().timestamp();
            }
            return this;
        }

        @Override
        public String toString() {
            return JSON.toJSONString(this);
        }
    }

    /**
     * 以当前时间 构造时间处理对象
     *
     * @return {@link Dates}
     */
    public static Dates now() {
        return new Dates();
    }

    /**
     * 构造时间处理对象：指定时间
     *
     * @param value {@link LocalDateTime}
     * @return {@link Dates}
     */
    public static Dates of(final LocalDateTime value) {
        Objects.requireNonNull(value, "参数【value】是必须的");
        return new Dates(value);
    }

    /**
     * 构造时间处理对象：指定时间
     *
     * @param value {@link LocalDate}
     * @return {@link Dates}
     */
    public static Dates of(final LocalDate value) {
        Objects.requireNonNull(value, "参数【value】是必须的");
        return new Dates(value.atStartOfDay());
    }

    /**
     * 构造时间处理对象：指定时间
     *
     * @param value {@link LocalTime}
     * @return {@link Dates}
     */
    public static Dates of(final LocalTime value) {
        Objects.requireNonNull(value, "参数【value】是必须的");
        return new Dates(value.atDate(LocalDate.now()));
    }

    /**
     * 构造时间处理对象：指定时间
     *
     * @param value {@link Timestamp}
     * @return {@link Dates}
     */
    public static Dates of(final Timestamp value) {
        Objects.requireNonNull(value, "参数【value】是必须的");
        return new Dates(value.toLocalDateTime());
    }

    /**
     * 构造时间处理对象：指定时间
     *
     * @param value {@link Date}
     * @return {@link Dates}
     */
    public static Dates of(final Date value) {
        Objects.requireNonNull(value, "参数【value】是必须的");
        return new Dates(new Timestamp(value.getTime()).toLocalDateTime());
    }

    /**
     * 构造时间处理对象：指定时间
     *
     * @param value long
     * @return {@link Dates}
     */
    public static Dates of(final long value) {
        return new Dates(LocalDateTime.ofInstant(Instant.ofEpochMilli(value), ZoneId.systemDefault()));
    }

    /**
     * 构造时间处理对象：指定时间
     *
     * @param value   String 日期字符串
     * @param pattern {@link Pattern} 日期格式
     * @return {@link Dates}
     * @deprecated 替代方法 {@link Pattern#parse(String)}
     */
    @Deprecated
    public static Dates of(final String value, final Pattern pattern) {
        Objects.requireNonNull(value, "参数【value】是必须的");
        Objects.requireNonNull(pattern, "参数【pattern】是必须的");
        return pattern.parse(value);
    }

    /**
     * 构造时间处理对象
     *
     * @param value String 日期字符串
     * @return {@link Dates}
     */
    public static Dates parse(String value) {
        Objects.requireNonNull(value, "参数【value】是必须的");
        Objects.requireNonNull("".equals(value.trim()) ? null : "", "参数【value】是必须的");
        try {
            value = value.trim().replaceAll("[^\\d]", "");
            String pattern;
            switch (value.length()) {
                case 17:
                    pattern = Integer.parseInt(value.substring(4, 6)) > 12 ? "yyyyddMMHHmmssSSS" : "yyyyMMddHHmmssSSS";
                    break;
                case 14:
                    pattern = Integer.parseInt(value.substring(4, 6)) > 12 ? "yyyyddMMHHmmss" : "yyyyMMddHHmmss";
                    break;
                case 9:
                    pattern = "HHmmssSSS";
                    break;
                case 8:
                    pattern = Integer.parseInt(value.substring(4, 6)) > 12 ? "yyyyddMM" : "yyyyMMdd";
                    break;
                case 6:
                    pattern = "HHmmss";
                    break;
                default:
                    throw new IllegalArgumentException("未识别的日期格式:".concat(value));
            }
            return of(new SimpleDateFormat(pattern, Locale.CHINA).parse(value).getTime());
//            return new Dates(LocalDateTime.parse(value, DateTimeFormatter.ofPattern(pattern)));
        } catch (ParseException e) {
            log.error(e.getMessage(), e);
            throw new IllegalArgumentException(String.format("日期转换失败，value:%s", value));
        }
    }

    private Dates() {
        this.value = LocalDateTime.now();
    }

    private Dates(final LocalTime value) {
        this(value.atDate(LocalDate.now()));
    }

    private Dates(final LocalDate value) {
        this(value.atStartOfDay());
    }

    private Dates(final LocalDateTime value) {
        this.value = value;
    }

    private LocalDateTime value;

    /**
     * @return {@link LocalDateTime}
     */
    public LocalDateTime get() {
        return value;
    }

    /**
     * 转换为long
     *
     * @return long
     */
    public long getTimeMillis() {
        return Timestamp.valueOf(value).getTime();
    }

    /**
     * 转换为 Timestamp
     *
     * @return {@link Timestamp}
     */
    public Timestamp timestamp() {
        return Timestamp.valueOf(value);
    }

    /**
     * 转换为 Date
     *
     * @return {@link Date}
     */
    public Date date() {
        return Timestamp.valueOf(value);
    }

    /**
     * 格式化为字符串, 必须指定格式
     *
     * @param pattern {@link Pattern}
     * @return String
     */
    public String format(final Pattern pattern) {
        Objects.requireNonNull(pattern, "参数【pattern】是必须的");
        return pattern.format(value);
    }

    /**
     * 格式化为字符串, 必须指定格式
     *
     * @param pattern {@link Pattern}
     * @return String
     * @deprecated 方法已废弃，请使用线程安全的日期函数 {@link this#format(Pattern)}
     */
    @Deprecated
    public String format(final String pattern) {
        Objects.requireNonNull(pattern, "参数【pattern】是必须的");
        return value.format(DateTimeFormatter.ofPattern(pattern));
    }

    /**
     * 格式化为字符串, 示例：yyyy-MM-dd
     *
     * @return String
     */
    public String formatDate() {
        return yyyy_MM_dd.format(value);
    }

    /**
     * 格式化为字符串, 示例：HH:mm:ss
     *
     * @return String
     */
    public String formatTime() {
        return HH_mm_ss.format(value);
    }

    /**
     * 格式化为字符串, 示例：yyyy-MM-dd HH:mm:ss
     *
     * @return String
     */
    public String formatDateTime() {
        return yyyy_MM_dd_HH_mm_ss.format(value);
    }

    /**
     * 格式化为字符串, 示例：yyyy-MM-dd HH:mm:ss.SSS
     *
     * @return String
     */
    public String formatAllDateTime() {
        return yyyy_MM_dd_HH_mm_ss_SSS.format(value);
    }

    /**
     * 获取：年
     *
     * @return int
     */
    public int year() {
        return value.getYear();
    }

    /**
     * 获取：月
     *
     * @return int
     */
    public int month() {
        return value.getMonthValue();
    }

    /**
     * 获取：日
     *
     * @return int
     */
    public int day() {
        return value.getDayOfMonth();
    }


    /**
     * 获取：时
     *
     * @return int
     */
    public int h() {
        return value.getHour();
    }

    /**
     * 获取：分
     *
     * @return int
     */
    public int m() {
        return value.getMinute();
    }

    /**
     * 获取：秒
     *
     * @return int
     */
    public int s() {
        return value.getSecond();
    }

    /**
     * 获取：微秒
     *
     * @return int
     */
    public int ns() {
        return value.getNano();
    }

    /**
     * 指定：年
     *
     * @param value int
     * @return {@link Dates}
     */
    public Dates year(int value) {
        this.value = this.value.withYear(value);
        return this;
    }

    /**
     * 指定：月
     *
     * @param value int
     * @return {@link Dates}
     */
    public Dates month(int value) {
        this.value = this.value.withMonth(Math.min(12, value));
        return this;
    }

    /**
     * 指定：日
     *
     * @param value int
     * @return {@link Dates}
     */
    public Dates day(int value) {
        this.value = this.value.withDayOfMonth(Math.min(value, this.value.with(TemporalAdjusters.lastDayOfMonth()).getDayOfMonth()));
        return this;
    }

    /**
     * 指定：时
     *
     * @param value int
     * @return {@link Dates}
     */
    public Dates h(int value) {
        this.value = this.value.withHour(Math.min(23, value));
        return this;
    }

    /**
     * 指定：分
     *
     * @param value int
     * @return {@link Dates}
     */
    public Dates m(int value) {
        this.value = this.value.withMinute(Math.min(59, value));
        return this;
    }

    /**
     * 指定：秒
     *
     * @param value int
     * @return {@link Dates}
     */
    public Dates s(int value) {
        this.value = this.value.withSecond(Math.min(59, value));
        return this;
    }

    /**
     * 指定：纳秒
     *
     * @param value int
     * @return {@link Dates}
     */
    public Dates ns(int value) {
        this.value = this.value.withNano(value);
        return this;
    }

    /**
     * 年【增加|减少】
     *
     * @param value int 正数为增加，负数表示减少
     * @return {@link Dates}
     */
    public Dates addYear(int value) {
        this.value = this.value.plusYears(value);
        return this;
    }

    /**
     * 月【增加|减少】
     *
     * @param value int 正数为增加，负数表示减少
     * @return {@link Dates}
     */
    public Dates addMonth(int value) {
        this.value = this.value.plusMonths(value);
        return this;
    }

    /**
     * 日【增加|减少】
     *
     * @param value int 正数为增加，负数表示减少
     * @return {@link Dates}
     */
    public Dates addDay(int value) {
        this.value = this.value.plusDays(value);
        return this;
    }

    /**
     * 星期【增加|减少】
     *
     * @param value int 正数为增加，负数表示减少
     * @return {@link Dates}
     */
    public Dates addWeek(int value) {
        this.value = this.value.plusWeeks(value);
        return this;
    }

    /**
     * 时【增加|减少】
     *
     * @param value int 正数为增加，负数表示减少
     * @return {@link Dates}
     */
    public Dates addHour(int value) {
        this.value = this.value.plusHours(value);
        return this;
    }

    /**
     * 分【增加|减少】
     *
     * @param value int 正数为增加，负数表示减少
     * @return {@link Dates}
     */
    public Dates addMinute(int value) {
        this.value = this.value.plusMinutes(value);
        return this;
    }

    /**
     * 秒【增加|减少】
     *
     * @param value int 正数为增加，负数表示减少
     * @return {@link Dates}
     */
    public Dates addSecond(int value) {
        this.value = this.value.plusSeconds(value);
        return this;
    }

    /**
     * 计算并设置为上周一的日期
     *
     * @return {@link Dates}
     */
    public Dates prevMonday() {
        value = value.minusWeeks(1) // minusWeeks(1) ；即上周
                .minusDays(value.getDayOfWeek().ordinal()) // 设置为周一
        ;
//        obj = obj.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); // 上一个周一，当前为周一时有bug
        return this;
    }

    /**
     * 计算并设置为下周一的日期
     *
     * @return {@link Dates}
     */
    public Dates nextMonday() {
        value = value.plusWeeks(1) // plusWeeks(1) ；即下周
                .minusDays(value.getDayOfWeek().ordinal()) // 设置为周一
        ;
//        obj = obj.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));  // 下一个周一，当前为周一时有bug
        return this;
    }

    /**
     * 当天的开始时间
     * 设置为当天 0 时 0 分 0 秒 0 纳秒
     *
     * @return {@link Dates}
     */
    public Dates beginTimeOfDay() {
        h(0).m(0).s(0).ns(0);
        return this;
    }

    /**
     * 当前小时时间
     * 设置为当前小时 x 时 0 分 0 秒 0 纳秒
     *
     * @return {@link Dates}
     */
    public Dates beginTimeOfMinute() {
        m(0).s(0).ns(0);
        return this;
    }

    /**
     * 当天的结束时间
     * 设置为当天 23 时 59 分 59 秒 999 纳秒
     *
     * @return {@link Dates}
     */
    public Dates endTimeOfDay() {
        h(23).m(59).s(59).ns(999998998);
        return this;
    }

    /**
     * 设置为当月第一天
     *
     * @return {@link Dates}
     */
    public Dates firstDayOfMonth() {
        day(1);
        return this;
    }

    /**
     * 设置为下个月1号
     *
     * @return {@link Dates}
     */
    public Dates firstDayOfNextMonth() {
        value = value.with(TemporalAdjusters.firstDayOfNextMonth());
        return this;
    }

    /**
     * 设置为当月最后一天
     *
     * @return {@link Dates}
     */
    public Dates lastDayOfMonth() {
        value = value.with(TemporalAdjusters.lastDayOfMonth());
        return this;
    }

    /**
     * 设置为上月最后一天
     *
     * @return {@link Dates}
     */
    public Dates lastDayOfPrevMonth() {
        value = value.plusMonths(-1).with(TemporalAdjusters.lastDayOfMonth());
        return this;
    }

    /**
     * 比对两个日期
     * <pre>
     *     小于 destDate 返回 -1；左小，右大；2018-01-01 | 2018-01-02=-1
     *     大于 destDate 返回 1； 左大，右小；2018-01-02 | 2018-01-01= 1
     *     相等返回 0
     *
     * @param destDate Dates
     * @return int
     */
    public int compare(Dates destDate) {
        final long value = this.getTimeMillis() - destDate.getTimeMillis();
        if (value < 0) return -1;
        if (value > 0) return 1;
        return 0;
    }

    /**
     * 比对两个日期，左边 > 右边
     *
     * @param destDate Dates
     * @return boolean
     */
    public boolean gt(Dates destDate) {
        return 1 == compare(destDate);
    }

    /**
     * 比对两个日期，左边 < 右边
     *
     * @param destDate Dates
     * @return boolean
     */
    public boolean lt(Dates destDate) {
        return -1 == compare(destDate);
    }

    /**
     * 比对两个日期，左边 >= 右边
     *
     * @param destDate Dates
     * @return boolean
     */
    public boolean ge(Dates destDate) {
        return -1 != compare(destDate);
    }

    /**
     * 比对两个日期，左边 <= 右边
     *
     * @param destDate Dates
     * @return boolean
     */
    public boolean le(Dates destDate) {
        return 1 != compare(destDate);
    }

    /**
     * 比对两个日期，左边 == 右边
     *
     * @param destDate Dates
     * @return boolean
     */
    public boolean eq(Dates destDate) {
        return 0 == compare(destDate);
    }

    /**
     * 获取时间间隔
     *
     * @return {@link Duration}
     */
    public Duration getTimeConsuming() {
// import java.time.Duration;
// import java.time.Period;
        return Duration.between(value, LocalDateTime.now());
    }

    /**
     * 获取时间间隔，m分s秒
     *
     * @return String
     */
    public String getTimeConsumingText() {
        final Duration duration = getTimeConsuming();
        return (Math.abs(duration.toHours()) > 0 ? String.format("%d时", duration.toHours()) : "")
                .concat(Math.abs(duration.toMinutes()) > 0 ? String.format("%d分", duration.toMinutes() % 60) : "")
                .concat(String.format("%d秒", (duration.toMillis() / 1000) % 60));
    }

    /**
     * 绝对值：获取两个日期之间相差的天数
     * abs(目标日期destDate - 当前dates)
     *
     * @param destDate Dates 目标日期
     * @return int 相差天数
     */
    public int getDifferDay(Dates destDate) {
        return Math.abs((int) Duration.between(value, destDate.get()).toDays());
    }

    /**
     * 获取两个日期之间相差的天数
     * 获取dates-目标日期destDate
     *
     * @param destDate Dates 目标日期
     * @return int 相差天数
     */
    public int getNoAbsDifferDay(Dates destDate) {
        return (int) Duration.between(destDate.get(), value).toDays();
    }

    /**
     * 获取本年按季度划分的时间区间集合
     * 数据示例：[{"begin":"2017-01-01 00:00:00","end":"2017-03-31 23:59:59"}, {"begin":"2017-04-01 00:00:00","end":"2017-06-30 23:59:59"}, {"begin":"2017-07-01 00:00:00","end":"2017-09-30 23:59:59"}, {"begin":"2017-10-01 00:00:00","end":"2017-12-31 23:59:59"}]
     *
     * @return {@link List}{@link List<Range>}
     */
    public List<Range> getRangeOfQuarter() {
        return Stream.of(
                new int[]{1, 3},
                new int[]{4, 6},
                new int[]{7, 9},
                new int[]{10, 12}
        )
                .map(arr -> Range.builder()
                        .begin(month(arr[0]).firstDayOfMonth().beginTimeOfDay().timestamp())
                        .end(month(arr[1]).lastDayOfMonth().endTimeOfDay().timestamp())
                        .build()
                )
                .collect(Collectors.toList());
    }

    /**
     * 获取当月时间区间
     * 数据示例：{"begin":"2017-01-01 00:00:00","end":"2017-1-31 23:59:59"}
     *
     * @return {@link Range}
     */
    public Range getRangeOfMonth() {
        return Range.builder()
                .begin(firstDayOfMonth().beginTimeOfDay().timestamp())
                .end(lastDayOfMonth().endTimeOfDay().timestamp())
                .build();
    }

    /**
     * 获取当年时间区间
     * 数据示例：{"begin":"2017-01-01 00:00:00","end":"2017-12-31 23:59:59"}
     *
     * @return {@link Range}
     */
    public Range getRangeOfYear() {
        return Range.builder()
                .begin(month(1).firstDayOfMonth().beginTimeOfDay().timestamp())
                .end(month(12).lastDayOfMonth().endTimeOfDay().timestamp())
                .build();
    }

    @Override
    public String toString() {
        return Objects.toString(value);
    }

    public static void main(String[] args) {
        log.info("当前时间：{}", Dates.now());
        log.info("当天 0 时 0 分 0 秒 0 毫秒：{}", Dates.now().beginTimeOfDay());
        log.info("当天 23 时 59 分 59 秒 999 毫秒：{}", Dates.now().endTimeOfDay());
        log.info("上周一：{}", Dates.now().prevMonday());
        log.info("下周一：{}", Dates.now().nextMonday());
        log.info("本月 1 号：{}", Dates.now().firstDayOfMonth());
        log.info("本月最后一天：{}", Dates.now().lastDayOfMonth());
        log.info("上月最后一天：{}", Dates.now().lastDayOfPrevMonth());
        log.info("下月 1 号：{}", Dates.now().firstDayOfNextMonth());
        log.info("天数差：当前日期 与 下个月 1 号：{}", Dates.now().getDifferDay(Dates.now().firstDayOfNextMonth()));
        log.info("天数差：当前日期 与 上个月最后一天：{}", Dates.now().getDifferDay(Dates.now().lastDayOfPrevMonth()));
        log.info("当月时间区间：{}", Dates.now().getRangeOfMonth());
        log.info("当年时间区间：{}", Dates.now().getRangeOfYear());
        log.info("年 +1 ：{}", Dates.now().addYear(1));
        log.info("年 -1 ：{}", Dates.now().addYear(-1));
        log.info("月 +1 ：{}", Dates.now().addMonth(1));
        log.info("月 -1 ：{}", Dates.now().addMonth(-1));
        log.info("日 +1 ：{}", Dates.now().addDay(1));
        log.info("日 -1 ：{}", Dates.now().addDay(-1));
        log.info("时 +1 ：{}", Dates.now().addHour(1));
        log.info("时 -1 ：{}", Dates.now().addHour(-1));
        log.info("分 +1 ：{}", Dates.now().addMinute(1));
        log.info("分 -1 ：{}", Dates.now().addMinute(-1));
        log.info("秒 +1 ：{}", Dates.now().addSecond(1));
        log.info("秒 -1 ：{}", Dates.now().addSecond(-1));
        log.info("formatTime:{}=>{}", Dates.now(), Dates.now().formatTime());
        log.info("formatDate:{}=>{}", Dates.now(), Dates.now().formatDate());
        log.info("formatDateTime:{}=>{}", Dates.now(), Dates.now().formatDateTime());
        log.info("json 反序列化：{}", JSON.parseObject("{\"begin\":\"2017-11-01\",\"end\":\"2017-11-30\"}", Range.class).rebuild());
        log.info("当前日期对比上个月最后一天：{}", Dates.now().compare(Dates.now().lastDayOfPrevMonth()));
        log.info("当前日期对比下个月 1 号：{}", Dates.now().compare(Dates.now().firstDayOfNextMonth()));
        log.info("当前日期与上个月最后一天：{}", Dates.now().compare(Dates.now().lastDayOfPrevMonth()));
        log.info("左 > 右 true：{}", Dates.now().addDay(1).gt(Dates.now()));
        log.info("左 > 右 false：{}", Dates.now().gt(Dates.now().addDay(1)));
        log.info("左 < 右 true：{}", Dates.now().lt(Dates.now().addDay(1)));
        log.info("左 < 右 false：{}", Dates.now().addDay(1).lt(Dates.now()));
        log.info("左 = 右 true：{}", Dates.now().beginTimeOfDay().eq(Dates.now().beginTimeOfDay()));
        log.info("左 = 右 false：{}", Dates.now().addDay(1).beginTimeOfDay().eq(Dates.now().beginTimeOfDay()));
        log.info("左 >= 右 true：{}", Dates.now().beginTimeOfDay().ge(Dates.now().beginTimeOfDay()));
        log.info("左 >= 右 true：{}", Dates.now().addDay(1).beginTimeOfDay().ge(Dates.now().beginTimeOfDay()));
        log.info("左 >= 右 false：{}", Dates.now().beginTimeOfDay().ge(Dates.now().addDay(1).beginTimeOfDay()));
        log.info("左 <= 右 true：{}", Dates.now().beginTimeOfDay().le(Dates.now().beginTimeOfDay()));
        log.info("左 <= 右 true：{}", Dates.now().beginTimeOfDay().le(Dates.now().addDay(1).beginTimeOfDay()));
        log.info("左 <= 右 false：{}", Dates.now().addDay(1).beginTimeOfDay().le(Dates.now().beginTimeOfDay()));
        for (Pattern pattern : values()) {
            log.info("{}.now():{} => {}", pattern.name(), pattern.comment, pattern.now());
        }
        for (Pattern pattern : values()) {
            log.info("{}.format({}) => {}", pattern.name(), pattern.comment, pattern.format(LocalDateTime.now()));
            if (pattern.adapter instanceof IDatePatternAdapter || pattern.adapter instanceof IDateTimePatternAdapter) {
                log.info("{}.format({}) => {}", pattern.name(), pattern.comment, pattern.format(LocalDate.now()));
            }
            if (pattern.adapter instanceof ITimePatternAdapter || pattern.adapter instanceof IDateTimePatternAdapter) {
                log.info("{}.format({}) => {}", pattern.name(), pattern.comment, pattern.format(LocalTime.now()));
            }
        }
        Stream.of(
                yyyy_MM_dd_HH_mm_ss_SSS,
                yyyy_MM_dd_HH_mm_ss,
                yyyy_MM_dd,
                yyyy_MM
        ).forEach(pattern -> {
            Stream.of(
                    "2019-10-11 10:01:11.888",
                    "2019-10-11 10:01:11",
                    "2019-10-11"
            ).forEach(value -> {
                log.info("{}.parse({}) => {}", pattern.name(), pattern.comment, pattern.parse(value));
            });
        });
        Stream.of(
                HH_mm_ss
        ).forEach(pattern -> {
            Stream.of(
                    "2019-10-11 10:01:11.888",
                    "2019-10-11 10:01:11",
                    "10:01:11"
            ).forEach(value -> {
                log.info("{}.parse({}) => {}", pattern.name(), pattern.comment, pattern.parse(value));
            });
        });
        Stream.of(
                HH_mm
        ).forEach(pattern -> {
            Stream.of(
                    "10:01:11",
                    "10:01"
            ).forEach(value -> {
                log.info("{}.parse({}) => {}", pattern.name(), pattern.comment, pattern.parse(value));
            });
        });
        Stream.of(
                yyyyMMddHHmmssSSS
        ).forEach(pattern -> {
            Stream.of(
                    "20191011100111888"
            ).forEach(value -> {
                log.info("{}.parse({}) => {}", pattern.name(), pattern.comment, pattern.parse(value));
            });
        });
        Stream.of(
                yyyyMMddHHmmss,
                yyyyMMdd
        ).forEach(pattern -> {
            Stream.of(
                    "20191011100111",
                    "20191011"
            ).forEach(value -> {
                log.info("{}.parse({}) => {}", pattern.name(), pattern.comment, pattern.parse(value));
            });
        });
        Stream.of(
                yyyyMM
        ).forEach(pattern -> {
            Stream.of(
                    "201910"
            ).forEach(value -> {
                log.info("{}.parse({}) => {}", pattern.name(), pattern.comment, pattern.parse(value));
            });
        });
        Stream.of(
                U_yyyy_MM_dd_HH_mm_ss_SSS
        ).forEach(pattern -> {
            Stream.of(
                    "2019/10/11 10:01:11.888",
                    "2019/10/11 10:01:11",
                    "2019/10/11"
            ).forEach(value -> {
                log.info("{}.parse({}) => {}", pattern.name(), pattern.comment, pattern.parse(value));
            });
        });
        Stream.of(
                U_yyyy_MM_dd_HH_mm_ss,
                U_yyyy_MM_dd
        ).forEach(pattern -> {
            Stream.of(
                    "2019/10/11 10:01:11",
                    "2019/10/11"
            ).forEach(value -> {
                log.info("{}.parse({}) => {}", pattern.name(), pattern.comment, pattern.parse(value));
            });
        });
        Stream.of(
                HHmmssSSS
        ).forEach(pattern -> {
            Stream.of(
                    "20191011100111888",
                    "20191011100111",
                    "100111888"
            ).forEach(value -> {
                log.info("{}.parse({}) => {}", pattern.name(), pattern.comment, pattern.parse(value));
            });
        });
        Stream.of(
                HHmmss
        ).forEach(pattern -> {
            Stream.of(
                    "100111"
            ).forEach(value -> {
                log.info("{}.parse({}) => {}", pattern.name(), pattern.comment, pattern.parse(value));
            });
        });

        log.info("{}.parse({}) => {}", yyyy_MM.name(), yyyy_MM.comment, yyyy_MM.parse("2019-1-2"));
        log.info("{}.parse({}) => {}", yyyy_MM_dd.name(), yyyy_MM_dd.comment, yyyy_MM_dd.parse("2019-1-2"));
        log.info("{}.parse({}) => {}", yyyy_MM_dd_HH_mm_ss.name(), yyyy_MM_dd_HH_mm_ss.comment, yyyy_MM_dd_HH_mm_ss.parse("2019-1-2 3:4:5.6"));
        log.info("{}.parse({}) => {}", HH_mm.name(), HH_mm.comment, HH_mm.parse("3:4:5.6"));
        log.info("{}.parse({}) => {}", HH_mm_ss.name(), HH_mm_ss.comment, HH_mm_ss.parse("3:4:5.6"));
        log.info("{}", yyyy_MM_dd.format(Dates.now().timestamp()));
        log.info("{}", yyyy_MM_dd.format(Dates.now().date()));
    }

}
