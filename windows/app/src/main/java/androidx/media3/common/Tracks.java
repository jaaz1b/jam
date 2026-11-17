package androidx.media3.common;

public final class Tracks {
    public static final Tracks EMPTY = new Tracks(java.util.Collections.emptyList());
    private final java.util.List<Group> groups;
    public Tracks(java.util.List<Group> g) { groups = g; }
    public java.util.List<Group> getGroups() { return groups; }
    public boolean isEmpty() { return groups.isEmpty(); }
    public boolean containsType(int t) { for (Group g : groups) if (g.type == t) return true; return false; }
    public static final class Group { public final int type, length; private final Format format;
        public Group(int type, Format f) { this.type = type; this.length = 1; format = f; }
        public Format getTrackFormat(int i) { return format; } public boolean isTrackSelected(int i) { return true; } public boolean isSelected() { return true; } public int getType() { return type; } }
}
