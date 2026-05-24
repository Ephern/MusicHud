package indi.etern.musichud.client.ui.utils.lyrics;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.LyricInfo;
import indi.etern.musichud.beans.music.LyricLine;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.client.ui.utils.lyrics.beans.MetaInfoLine;
import indi.etern.musichud.interfaces.ClientConfig;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FullLineLyricParser {
    private static final Pattern mainPattern = Pattern.compile("\\[[0-9:.]+].*");
    private static final DateTimeFormatter TIME_FORMATTER = new DateTimeFormatterBuilder()
            .appendPattern("HH:mm:ss")
            .appendFraction(java.time.temporal.ChronoField.MILLI_OF_SECOND, 1, 3, true)
            .toFormatter();
    private static final Duration emptyLineIgnoreDuration = Duration.ofSeconds(5);
    private static final Logger logger = MusicHud.getLogger(FullLineLyricParser.class);
    private static final ClientConfig clientConfig = ClientConfig.getInstance();

    public static ArrayDeque<LyricLine> parse(MusicDetail musicDetail) {
        LyricInfo lyricInfo = musicDetail.getLyricInfo();
        String lyric = lyricInfo.getLyric().getLyric();
        String translatedLyric = lyricInfo.getTranslatedLyric().getLyric();
        LinkedHashMap<Duration, LyricLine> map = new LinkedHashMap<>();
        List<LyricLine> lyricLinesWithoutValidTimestamp = new ArrayList<>(0);
        matchLine(lyric, (metaData) -> {
            Duration startTime = metaData.startTime;
            LyricLine lyricLine = map.get(startTime);
            String lyricString = metaData.lyric == null ? "" : metaData.lyric.replace('\u00A0', ' ').replace('\n', ' ').trim();
            lyricString = lyricString.replace('\n', ' ').trim();
            if (lyricLine == null) {
                lyricLine = LyricLine.builder()
                        .startTime(startTime)
                        .text(lyricString)
                        .type(metaData.type)
                        .build();
                if (startTime != null) {
                    map.put(startTime, lyricLine);
                } else if (lyricLine.getText() != null && !lyricLine.getText().startsWith("}")) {
                    lyricLinesWithoutValidTimestamp.add(lyricLine);
                }
            } else if (!lyricString.isEmpty()) {
                lyricLine.setText(lyricLine.getText() + "\n" + lyricString);
            }
        });
        matchLine(translatedLyric, (metaData) -> {
            Duration startTime = metaData.startTime;
            LyricLine lyricLine = map.get(startTime);
            if (lyricLine == null) {
                lyricLine = LyricLine.builder()
                        .startTime(startTime)
                        .build();
                if (startTime != null) {
                    map.put(startTime, lyricLine);
                } else {
                    lyricLinesWithoutValidTimestamp.add(lyricLine);
                }
            }
            String s = metaData.lyric;
            String lyricLineTranslatedText = lyricLine.getTranslatedText();
            if (s != null && !s.isEmpty()) {
                s = s.replace('\n', ' ').replace('\u00A0', ' ').trim();
                if (lyricLineTranslatedText == null || lyricLineTranslatedText.isEmpty()) {
                    lyricLine.setTranslatedText(s);
                } else {
                    lyricLine.setTranslatedText(lyricLineTranslatedText + "\n" + s);
                }
            } else {
                if (lyricLineTranslatedText == null || lyricLineTranslatedText.isEmpty()) {
                    lyricLine.setTranslatedText("");
                } else {
                    lyricLine.setTranslatedText(lyricLineTranslatedText);
                }
            }
        });
        ArrayDeque<LyricLine> lyricLines = new ArrayDeque<>(lyricLinesWithoutValidTimestamp);
        lyricLines.addAll(map.values());
        List<LyricLine> list = lyricLines.stream().sorted(Comparator.comparing(LyricLine::getStartTime)).toList();
        lyricLines.clear();
        int nextIndex = 1;
        LyricLine lastLyricLine = null;
        LyricLine firstNormalLyricLine = null;
        for (LyricLine lyricLine : list) {
            if (firstNormalLyricLine == null && lyricLine.getType() == LyricLine.Type.NORMAL) {
                firstNormalLyricLine = lyricLine;
                if (lastLyricLine == null || lastLyricLine.getType() == LyricLine.Type.META_DATA) {
                    Duration oneSec = Duration.ofSeconds(1);
                    Duration rhythmStartTime = lastLyricLine == null ? oneSec : lastLyricLine.getStartTime().plus(oneSec);
                    Duration rhythmDuration = lyricLine.getStartTime().minus(rhythmStartTime);
                    if (rhythmDuration.compareTo(emptyLineIgnoreDuration) > 0) {
                        if (lastLyricLine != null) {
                            lastLyricLine.setDuration(oneSec);
                        }
                        LyricLine rhythmLine = LyricLine.builder()
                                .startTime(rhythmStartTime)
                                .previous(lastLyricLine)
                                .type(LyricLine.Type.RHYTHM)
                                .text("")
                                .build();
                        lastLyricLine = rhythmLine;
                        lyricLines.add(rhythmLine);
                    }
                }
            }
            if (lastLyricLine != null) {
                lastLyricLine.setDuration(lyricLine.getStartTime().minus(lastLyricLine.getStartTime()));
                lastLyricLine.setNext(lyricLine);
                lyricLine.setPrevious(lastLyricLine);
            }
            lastLyricLine = lyricLine;
            String text = lyricLine.getText();
            if (text == null || text.isEmpty()) {
                String translatedText = lyricLine.getTranslatedText();
                if (translatedText == null || translatedText.isEmpty()) {
                    if (nextIndex < list.size()) {
                        Duration minus = list.get(nextIndex).getStartTime().minus(lyricLine.getStartTime());
                        if (minus.compareTo(emptyLineIgnoreDuration) > 0) {
                            lyricLine.setType(LyricLine.Type.RHYTHM);
                            lyricLine.setText("");
                            lyricLines.add(lyricLine);
                        } else {
                            logger.debug("An empty lyric line is ignored due to its duration ({} s)", minus.toSeconds());
                        }
                    } else {
                        logger.debug("An empty lyric line is ignored due to its position (last one)");
                    }
                }
            } else {
                lyricLines.add(lyricLine);
            }
            nextIndex += 1;
        }
        if (lastLyricLine != null && lastLyricLine.getDuration() == null) {
            lastLyricLine.setDuration(Duration.ofMillis(musicDetail.getDurationMillis()).minus(lastLyricLine.getStartTime()));
        }
        return lyricLines;
    }

    static Duration parseToDuration(String timeString) {
        try {
            String normalizedTime = timeString;
            String[] parts = timeString.split(":");

            if (parts.length == 2) {
                normalizedTime = "00:" + timeString;
            } else if (parts.length != 3) {
                throw new IllegalArgumentException("Invalid time format: " + timeString);
            }
            LocalTime localTime = LocalTime.parse(normalizedTime, TIME_FORMATTER);
            return Duration.between(LocalTime.MIDNIGHT, localTime);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid time format: " + timeString, e);
        }
    }

    static void matchLine(String lyric, Consumer<LyricLineMetaData> matchedConsumer) {
        List<MetaInfoLine> metaInfoLines = RegexJsonExtractor.extractJsonObjectsSafely(lyric, MetaInfoLine.class);
        metaInfoLines.forEach(metaInfoLine -> {
            matchedConsumer.accept(new LyricLineMetaData(metaInfoLine.getTimestampDuration(), metaInfoLine.getText(), LyricLine.Type.META_DATA));
        });
        Matcher matcher = mainPattern.matcher(lyric);
        while (matcher.find()) {
            String item = matcher.group();
            try {
                if (!item.contains(".")) {
                    int colonCount = Math.toIntExact(item.chars().filter(c -> c == ':').count());
                    int i = item.lastIndexOf(":");
                    StringBuilder stringBuilder = new StringBuilder(item);
                    if (colonCount == 2) {
                        stringBuilder.setCharAt(i, '.');
                    } else if (colonCount == 1) {
                        stringBuilder.insert(i + 3, ".000");
                    }
                    item = stringBuilder.toString();
                } else {
                    int i = item.indexOf(']');
                    int millisDigit = i - item.indexOf('.') - 1;
                    if (millisDigit < 3) {
                        StringBuilder stringBuilder = new StringBuilder(item);
                        stringBuilder.insert(i, "0".repeat(3 - millisDigit));
                        item = stringBuilder.toString();
                    }
                }
                String[] split = item.split("]", 2);
                String timestamp = split[0];
                String lyricLineContent = split[1];
                try {
                    Duration duration = parseToDuration(timestamp.substring(1, timestamp.length() - 1));
                    matchedConsumer.accept(new LyricLineMetaData(duration, lyricLineContent, LyricLine.Type.NORMAL));
                } catch (Exception ignored) {
                    matchedConsumer.accept(new LyricLineMetaData(null, lyricLineContent, LyricLine.Type.NORMAL));
                }
            } catch (Exception e) {
                logger.debug("failed to parse line \"{}\"", item);
            }
        }
    }

    record LyricLineMetaData(Duration startTime, String lyric, LyricLine.Type type) {
    }
}