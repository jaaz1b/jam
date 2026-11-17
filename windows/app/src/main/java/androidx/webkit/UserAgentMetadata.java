package androidx.webkit;
public class UserAgentMetadata {
    public static class BrandVersion {
        public static class Builder {
            public Builder setBrand(String b) { return this; } public Builder setMajorVersion(String v) { return this; } public Builder setFullVersion(String v) { return this; }
            public BrandVersion build() { return new BrandVersion(); }
        }
    }
    public static class Builder {
        public Builder setBrandVersionList(java.util.List<BrandVersion> l) { return this; } public Builder setFullVersion(String v) { return this; }
        public Builder setPlatform(String p) { return this; } public Builder setPlatformVersion(String v) { return this; } public Builder setArchitecture(String a) { return this; }
        public Builder setBitness(int b) { return this; } public Builder setMobile(boolean m) { return this; } public Builder setModel(String m) { return this; } public Builder setWow64(boolean w) { return this; }
        public UserAgentMetadata build() { return new UserAgentMetadata(); }
    }
}
