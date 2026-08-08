package indi.etern.musichud.client.utils.lyrics;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.LyricInfo;
import indi.etern.musichud.client.ui.dto.LyricLine;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.client.ui.dto.MetaInfoLine;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WordByWordLyricParser {
    private static final Pattern mainPattern = Pattern.compile("((?:\\[[0-9]+,[0-9]+])+)(.*)");
    private static final Pattern timestampPattern = Pattern.compile("\\[([0-9]+),([0-9]+)]");
    private static final Pattern phrasePattern = Pattern.compile("\\((\\d+),(\\d+),(\\d+)\\)([\\s\\S]*?)(?=\\(\\d+,\\d+,(\\d+)\\)|$)");
    private static final Duration emptyLineIgnoreDuration = Duration.ofSeconds(5);
    private static final Logger logger = MusicHud.getLogger(FullLineLyricParser.class);

    public static ArrayDeque<LyricLine> parse(MusicDetail musicDetail) {
        LyricInfo lyricInfo = musicDetail.getLyricInfo();
        String lyric = lyricInfo.getWordByWordLyric().getLyric();
        String translatedLyric = lyricInfo.getWordByWordTranslatedLyric().getLyric();
        LinkedHashMap<Duration, LyricLine> map = new LinkedHashMap<>();
        List<LyricLine> lyricLinesWithoutValidTimestamp = new ArrayList<>(0);
        matchLine(lyric, (metaData) -> {
            Duration startTime = metaData.startTime;
            LyricLine lyricLine = map.get(startTime);
            String lyricString = metaData.lyric == null ? "" : metaData.lyric;
            lyricString = lyricString.replace('\n', ' ').trim();
            if (lyricLine == null) {
                // Remove duration for smooth transition between lines
                lyricLine = LyricLine.builder()
                        .startTime(startTime)
                        .text(lyricString)
                        .type(metaData.type).build();
                if (startTime == null && lyricLine.getText() != null && !lyricLine.getText().startsWith("}")) {
                    lyricLinesWithoutValidTimestamp.add(lyricLine);
                }
            } else if (!lyricString.isEmpty()) {
                lyricLine.setText(lyricLine.getText() + "\n" + lyricString);
            }
            if (metaData.phraseEndingOffsetMap != null) {
                lyricLine.getPhraseEndingOffsetMap().putAll(metaData.phraseEndingOffsetMap);
                lyricLine.setWordByWord(true);
            }
            map.put(startTime, lyricLine);
        });
        FullLineLyricParser.matchLine(translatedLyric, (metaData) -> {
            Duration startTime = metaData.startTime();
            LyricLine lyricLine = map.get(startTime);
            if (lyricLine == null) {
                lyricLine = LyricLine.builder().startTime(startTime).build();
                if (startTime != null) {
                    map.put(startTime, lyricLine);
                } else {
                    lyricLinesWithoutValidTimestamp.add(lyricLine);
                }
            }
            String lyric1 = metaData.lyric();
            if (lyric1 != null) {
                lyricLine.setTranslatedText(lyric1.replace('\u00A0', ' ').trim());
            } else {
                lyricLine.setTranslatedText("");
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
                if (lastLyricLine.getDuration() == null) {
                    lastLyricLine.setDuration(lyricLine.getStartTime().minus(lastLyricLine.getStartTime()));
                }
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

    static void matchLine(String lyric, Consumer<LyricLineMetaData> matchedConsumer) {
        List<MetaInfoLine> metaInfoLines = RegexJsonExtractor.extractJsonObjectsSafely(lyric, MetaInfoLine.class);
        metaInfoLines.forEach(metaInfoLine -> {
            matchedConsumer.accept(new LyricLineMetaData(metaInfoLine.getTimestampDuration(), null, metaInfoLine.getText(), LyricLine.Type.META_DATA, null));
        });
        Matcher lineMatcher = mainPattern.matcher(lyric);
        Duration lastLineEnd = Duration.ZERO;
        while (lineMatcher.find()) {
            String timestampGroups = lineMatcher.group(1);
            String lineRawText = lineMatcher.group(2);
            Matcher timestampMatcher = timestampPattern.matcher(timestampGroups);
            while (timestampMatcher.find()) {
                Map<Duration, Integer> phrases = new LinkedHashMap<>();
                String lineStartTimestamp = timestampMatcher.group(1);
                String lineDurationString = timestampMatcher.group(2);
                Duration lineStart = Duration.ofMillis(Long.parseLong(lineStartTimestamp));

                Duration interval = lineStart.minus(lastLineEnd);
                if (interval.compareTo(emptyLineIgnoreDuration) > 0) {
                    matchedConsumer.accept(new LyricLineMetaData(lastLineEnd, interval, "", LyricLine.Type.RHYTHM, null));
                }

                Duration nextPhraseStart = lineStart;
                Matcher phraseMatcher = phrasePattern.matcher(lineRawText);
                StringBuilder lineText = new StringBuilder();
                int charIndex = 0;
                while (phraseMatcher.find()) {
//                String phraseStartTimestamp = phraseMatcher.group(1);
                    String phraseDurationMillis = phraseMatcher.group(2);
//                String unknown = phraseMatcher.group(3);
                    String phraseText = phraseMatcher.group(4);
                    String suffix = phraseText.endsWith(" ") ? " " : "";
                    phraseText = phraseText.replace('\u00A0', ' ').replace("\n", "").trim() + suffix;
                    lineText.append(phraseText);
                    charIndex += phraseText.length();
                    nextPhraseStart = nextPhraseStart.plusMillis(Long.parseLong(phraseDurationMillis));
                    phrases.put(nextPhraseStart, charIndex);
                }
                Duration lineDuration = Duration.ofMillis(Long.parseLong(lineDurationString));
                Duration allPhraseDuration = nextPhraseStart.minus(lineStart);
                if (allPhraseDuration.compareTo(lineDuration) < 0) {
                    lineDuration = allPhraseDuration;
                }
                matchedConsumer.accept(new LyricLineMetaData(lineStart, lineDuration, lineText.toString(), LyricLine.Type.NORMAL, phrases));
                lastLineEnd = lineStart.plus(lineDuration);
            }
        }
    }

    record LyricLineMetaData(Duration startTime, Duration lineDuration, String lyric, LyricLine.Type type,
                             Map<Duration, Integer> phraseEndingOffsetMap) {
    }
}