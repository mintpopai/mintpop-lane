package ai.mintpop.lane.util;

import java.util.Comparator;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 桌面端版本号（SemVer 的 X.Y.Z 与可选预发布后缀），只用于「客户端是否落后于最新版」的比较。
 *
 * <p>预发布排在同号正式版之前（{@code 1.2.0-rc.1 < 1.2.0}），两个预发布之间按后缀字符串比较——
 * 预发布从不写进更新清单，不会成为「最新版」，这里只需保证它不被误判成比正式版新。
 */
public record ClientVersion(int major, int minor, int patch, String preRelease) implements Comparable<ClientVersion> {

    private static final Pattern SHAPE = Pattern.compile("^v?(\\d+)\\.(\\d+)\\.(\\d+)(?:-([0-9A-Za-z.-]+))?$");

    private static final Comparator<ClientVersion> ORDER = Comparator
            .comparingInt(ClientVersion::major)
            .thenComparingInt(ClientVersion::minor)
            .thenComparingInt(ClientVersion::patch)
            // 无后缀（正式版）排在有后缀之后
            .thenComparing(ClientVersion::preRelease, Comparator.nullsLast(Comparator.naturalOrder()));

    /** 解析失败（空、形状不对、数字溢出）返回空，由调用方决定按「旧版」处理还是忽略 */
    public static Optional<ClientVersion> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        Matcher m = SHAPE.matcher(raw.trim());
        if (!m.matches()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new ClientVersion(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                    Integer.parseInt(m.group(3)), m.group(4)));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    public boolean isOlderThan(ClientVersion other) {
        return compareTo(other) < 0;
    }

    @Override
    public int compareTo(ClientVersion other) {
        return ORDER.compare(this, other);
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch + (preRelease == null ? "" : "-" + preRelease);
    }
}
